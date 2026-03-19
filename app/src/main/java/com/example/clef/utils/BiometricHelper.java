package com.example.clef.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Gestiona el desbloqueo biométrico de Clef.
 *
 * Flujo de activación:
 *   1. El usuario activa el switch en Ajustes.
 *   2. Se genera una clave AES-256 en el Android Keystore, protegida por biometría.
 *   3. Se muestra el prompt biométrico para autorizar el cifrado.
 *   4. La DEK activa se cifra con esa clave y el resultado se guarda en SharedPreferences.
 *
 * Flujo de desbloqueo:
 *   1. UnlockActivity detecta que la biometría está habilitada.
 *   2. Se muestra el prompt biométrico con un Cipher preparado para descifrar.
 *   3. Si el usuario se autentica, el Cipher descifra la DEK almacenada.
 *   4. La DEK descifrada se entrega a SessionManager.unlock().
 *
 * La clave del Keystore se invalida automáticamente si el usuario añade
 * o elimina huellas del dispositivo (setInvalidatedByBiometricEnrollment = true).
 */
public class BiometricHelper {

    private static final String KEY_ALIAS        = "clef_biometric_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION   = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS     = 128;
    private static final int    GCM_IV_BYTES     = 12;

    private static final String PREFS_NAME  = "clef_biometric_prefs";
    private static final String PREF_ENC_DEK = "enc_dek"; // IV (12B) + ciphertext en Base64

    private BiometricHelper() {}


    // ── Disponibilidad ─────────────────────────────────────────────────────────

    /** true si el dispositivo tiene biometría de clase fuerte (huella/rostro). */
    public static boolean isAvailable(Context context) {
        return BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /** true si el usuario activó la biometría Y hay una DEK cifrada guardada. */
    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .contains(PREF_ENC_DEK);
    }


    // ── Activar biometría ──────────────────────────────────────────────────────

    public interface EnableCallback {
        void onSuccess();
        void onError(String message);
        void onCancelled();
    }

    /**
     * Activa la biometría cifrando la DEK activa con una clave del Keystore.
     * Requiere que la sesión esté desbloqueada (dek != null).
     * Muestra el prompt biométrico al usuario para autorizar el cifrado.
     */
    public static void enable(FragmentActivity activity, byte[] dek, EnableCallback callback) {
        if (dek == null) {
            callback.onError("Sesión no activa. Desbloquea primero con tu contraseña.");
            return;
        }
        try {
            generateKeyIfNeeded();
            Cipher encryptCipher = buildEncryptCipher();

            BiometricPrompt prompt = new BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            try {
                                Cipher c = result.getCryptoObject().getCipher();
                                byte[] encDek = c.doFinal(dek);
                                byte[] iv     = c.getIV();

                                // Guardamos IV + encDek concatenados como Base64
                                byte[] combined = new byte[iv.length + encDek.length];
                                System.arraycopy(iv,     0, combined, 0,         iv.length);
                                System.arraycopy(encDek, 0, combined, iv.length, encDek.length);

                                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit()
                                        .putString(PREF_ENC_DEK, Base64.encodeToString(combined, Base64.NO_WRAP))
                                        .apply();

                                callback.onSuccess();
                            } catch (Exception e) {
                                callback.onError("Error al cifrar la clave: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                    || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                callback.onCancelled();
                            } else {
                                callback.onError(errString.toString());
                            }
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            // Huella no reconocida — el sistema ya muestra feedback, no hacemos nada
                        }
                    });

            prompt.authenticate(
                    buildPromptInfo("Activar biometría", "Confirma tu identidad para habilitar el desbloqueo biométrico"),
                    new BiometricPrompt.CryptoObject(encryptCipher)
            );

        } catch (Exception e) {
            callback.onError("No se pudo preparar la biometría: " + e.getMessage());
        }
    }


    // ── Desactivar biometría ───────────────────────────────────────────────────

    /** Borra la DEK cifrada y elimina la clave del Keystore. */
    public static void disable(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_ENC_DEK)
                .apply();
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            ks.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {}
    }


    // ── Desbloquear con biometría ──────────────────────────────────────────────

    public interface UnlockCallback {
        void onSuccess(byte[] dek);
        void onError(String message);
        void onCancelled();
    }

    /**
     * Muestra el prompt biométrico y, si el usuario se autentica, descifra y
     * devuelve la DEK lista para pasar a SessionManager.unlock().
     */
    public static void unlock(FragmentActivity activity, UnlockCallback callback) {
        String encDekB64 = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_ENC_DEK, null);

        if (encDekB64 == null) {
            callback.onError("No hay biometría configurada.");
            return;
        }

        try {
            byte[] combined = Base64.decode(encDekB64, Base64.NO_WRAP);
            byte[] iv       = new byte[GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);

            Cipher decryptCipher = buildDecryptCipher(iv);

            BiometricPrompt prompt = new BiometricPrompt(
                    activity,
                    ContextCompat.getMainExecutor(activity),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            try {
                                byte[] encDek = new byte[combined.length - GCM_IV_BYTES];
                                System.arraycopy(combined, GCM_IV_BYTES, encDek, 0, encDek.length);

                                Cipher c   = result.getCryptoObject().getCipher();
                                byte[] dek = c.doFinal(encDek);
                                callback.onSuccess(dek);
                            } catch (Exception e) {
                                // Fallo al descifrar: posiblemente la clave fue invalidada
                                disable(activity);
                                callback.onError("La clave biométrica no es válida. Reactívala en Ajustes.");
                            }
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, CharSequence errString) {
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                    || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                callback.onCancelled();
                            } else {
                                callback.onError(errString.toString());
                            }
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            // El sistema muestra su propio feedback
                        }
                    });

            prompt.authenticate(
                    buildPromptInfo("Desbloquear Clef", "Usa tu huella o rostro para acceder"),
                    new BiometricPrompt.CryptoObject(decryptCipher)
            );

        } catch (Exception e) {
            // Si la clave fue invalidada (nuevas huellas añadidas), limpiamos y pedimos reactivar
            disable(activity);
            callback.onError("La clave biométrica fue invalidada. Reactívala en Ajustes.");
        }
    }


    // ── Privado ────────────────────────────────────────────────────────────────

    private static void generateKeyIfNeeded() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return;

        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build());
        kg.generateKey();
    }

    private static Cipher buildEncryptCipher() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher;
    }

    private static Cipher buildDecryptCipher(byte[] iv) throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher;
    }

    private static BiometricPrompt.PromptInfo buildPromptInfo(String title, String subtitle) {
        return new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Usar contraseña")
                .build();
    }
}
