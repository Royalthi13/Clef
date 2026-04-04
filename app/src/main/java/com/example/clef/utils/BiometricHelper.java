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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class BiometricHelper {

    private static final String KEY_ALIAS        = "clef_biometric_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION   = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS     = 128;
    private static final int    GCM_IV_BYTES     = 12;

    private static final String PREFS_NAME = "clef_biometric_prefs";

    private BiometricHelper() {}

    // ── Clave por UID ──────────────────────────────────────────────────────────
    // ANTES: siempre "enc_dek" → la DEK de usuario A era visible para usuario B.
    // AHORA: "enc_dek_<uid>" → cada usuario tiene su propia entrada.

    private static String getDekKey() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (u != null) ? u.getUid() : "anon";
        return "enc_dek_" + uid;
    }

    // ── Disponibilidad ─────────────────────────────────────────────────────────

    public static boolean isAvailable(Context context) {
        return BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /** true solo si hay una DEK cifrada guardada para el usuario ACTUAL. */
    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .contains(getDekKey());
    }

    // ── Activar biometría ──────────────────────────────────────────────────────

    public interface EnableCallback {
        void onSuccess();
        void onError(String message);
        void onCancelled();
    }

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

                                byte[] combined = new byte[iv.length + encDek.length];
                                System.arraycopy(iv,     0, combined, 0,         iv.length);
                                System.arraycopy(encDek, 0, combined, iv.length, encDek.length);

                                // Guardar con clave específica del usuario actual
                                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit()
                                        .putString(getDekKey(),
                                                Base64.encodeToString(combined, Base64.NO_WRAP))
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

                        @Override public void onAuthenticationFailed() {}
                    });

            prompt.authenticate(
                    buildPromptInfo("Activar biometría",
                            "Confirma tu identidad para habilitar el desbloqueo biométrico"),
                    new BiometricPrompt.CryptoObject(encryptCipher)
            );

        } catch (Exception e) {
            callback.onError("No se pudo preparar la biometría: " + e.getMessage());
        }
    }

    // ── Confirmar identidad (sin descifrar DEK) ────────────────────────────────

    public interface ConfirmCallback {
        void onConfirmed();
        void onCancelled();
    }

    /**
     * Muestra un BiometricPrompt de solo confirmación (sin CryptoObject).
     * Usa biometría fuerte + credencial del dispositivo (PIN/patrón/contraseña) como fallback.
     */
    public static void confirmIdentity(FragmentActivity activity, String title, String subtitle,
                                       ConfirmCallback callback) {
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG |
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        BiometricPrompt prompt = new BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        callback.onConfirmed();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        callback.onCancelled();
                    }

                    @Override public void onAuthenticationFailed() {}
                });

        prompt.authenticate(info);
    }

    // ── Desactivar biometría ───────────────────────────────────────────────────

    /** Borra SOLO la entrada del usuario actual, no las de otros usuarios. */
    public static void disable(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(getDekKey())
                .apply();
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            ks.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {}
    }

    /** Borra TODAS las entradas biométricas del dispositivo (llamar en signOut completo). */
    public static void disableAll(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();
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

    public static void unlock(FragmentActivity activity, UnlockCallback callback) {
        // Lee la clave del usuario ACTUAL — si es otro usuario, getDekKey() devuelve otro string
        String encDekB64 = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(getDekKey(), null);

        if (encDekB64 == null) {
            callback.onError("No hay biometría configurada para este usuario.");
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

                        @Override public void onAuthenticationFailed() {}
                    });

            prompt.authenticate(
                    buildPromptInfo("Desbloquear Clef",
                            "Usa tu huella o rostro para acceder"),
                    new BiometricPrompt.CryptoObject(decryptCipher)
            );

        } catch (Exception e) {
            disable(activity);
            callback.onError("La clave biométrica fue invalidada. Reactívala en Ajustes.");
        }
    }

    // ── Privado ────────────────────────────────────────────────────────────────

    private static void generateKeyIfNeeded() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return;

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
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