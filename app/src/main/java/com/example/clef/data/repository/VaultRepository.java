package com.example.clef.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.example.clef.crypto.KeyManager;
import com.example.clef.data.local.FileManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.remote.FirebaseManager.UserData;

/**
 * Puerta de entrada a todos los datos de la bóveda.
 *
 * Coordina FileManager (disco local) y FirebaseManager (nube).
 * Trabaja con el DTO UserData — nunca toca DocumentSnapshot
 * ni los nombres de campo internos de Firebase.
 *
 * Reglas:
 *   - Guardar:   primero disco (instantáneo), luego Firebase.
 *   - Cargar:    primero Firebase (datos frescos); si falla, disco local.
 *   - Registrar: sube todo en una sola llamada a Firebase.
 */
public class VaultRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private static final String KEY_PREFS  = "clef_key_cache";
    private static final String KEY_SALT   = "salt";
    private static final String KEY_CAJA_A = "caja_a";
    private static final String KEY_CAJA_B = "caja_b";
    private final Context context;
    private final FileManager     fileManager;
    private final FirebaseManager firebaseManager;
    private final SharedPreferences keyPrefs;

    public VaultRepository(Context context) {
        this.fileManager     = new FileManager(context);
        this.firebaseManager = new FirebaseManager();
        this.context = context;
        this.keyPrefs        = context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE);
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    /**
     * Sube todos los datos del registro a Firebase en una sola llamada.
     * Recibe el RegistrationBundle directamente de KeyManager.
     */
    public void registerUser(KeyManager.RegistrationBundle bundle, Callback<Void> callback) {
        firebaseManager.uploadAll(
                        bundle.saltBase64,
                        bundle.cajaABase64,
                        bundle.cajaBBase64,
                        bundle.bovedaCifradaBase64)
                .addOnSuccessListener(unused -> {
                    saveLocalVault(bundle.bovedaCifradaBase64);
                    // Cacheamos las claves para que el desbloqueo offline funcione desde el principio
                    keyPrefs.edit()
                            .putString(KEY_SALT,   bundle.saltBase64)
                            .putString(KEY_CAJA_A, bundle.cajaABase64)
                            .putString(KEY_CAJA_B, bundle.cajaBBase64)
                            .apply();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    // ── Guardar bóveda ────────────────────────────────────────────────────────

    /**
     * Guarda el vault cifrado en disco.
     * Recibe el String Base64 que devuelve KeyManager.cifrarVault().
     */
    public void saveVault(String encryptedVaultBase64, Callback<Void> callback) {
        saveLocalVault(encryptedVaultBase64);
        boolean syncEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("sync_enabled", false);
        if (syncEnabled) {
            exportToFirebase(callback);
        } else {
            callback.onSuccess(null);
        }
    }

    // ── Cargar datos del usuario ───────────────────────────────────────────────

    /**
     * Descarga los datos del usuario desde Firebase como UserData tipado.
     * Si falla la red, llama a onError con "offline_fallback" para que
     * la UI sepa que debe usar loadLocalVault().
     */
    public void loadUserData(Callback<UserData> callback) {
        firebaseManager.downloadUserData()
                .addOnSuccessListener(userData -> {
                    if (userData != null) {
                        if (userData.vault != null) saveLocalVault(userData.vault);
                        // Guardamos salt y cajaA para el modo offline
                        if (userData.salt != null && userData.cajaA != null) {
                            keyPrefs.edit()
                                    .putString(KEY_SALT,   userData.salt)
                                    .putString(KEY_CAJA_A, userData.cajaA)
                                    .putString(KEY_CAJA_B, userData.cajaB)
                                    .apply();
                        }
                    }
                    callback.onSuccess(userData);
                })
                .addOnFailureListener(e ->
                        callback.onError(new Exception("offline_fallback", e)));
    }

    /**
     * Lee el vault cifrado del disco y lo devuelve en Base64.
     * Usar solo cuando loadUserData() falla por falta de red.
     * Devuelve null si nunca se guardó nada en disco.
     */
    public String loadLocalVault() {
        byte[] bytes = readLocalVaultBytes();
        if (bytes == null) return null;
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    // ── Cambio de Contraseña Maestra ──────────────────────────────────────────

    /**
     * Actualiza la cajaA en Firebase tras un cambio de Contraseña Maestra.
     * Recibe el String Base64 de RecoveryResult.nuevaCajaABase64.
     */
    public void updateCajaA(String nuevaCajaABase64, Callback<Void> callback) {
        firebaseManager.uploadCajaA(nuevaCajaABase64)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    /**
     * Comprueba si el usuario ya tiene Contraseña Maestra configurada en Firebase.
     */
    public void userHasMasterPassword(Callback<Boolean> callback) {
        firebaseManager.userHasMasterPassword()
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    /**
     * Carga los datos de clave (salt + cajaA) cacheados para el modo offline.
     * Devuelve un UserData con el vault local, o null si no hay nada cacheado.
     */
    public UserData loadOfflineUserData() {
        String salt  = keyPrefs.getString(KEY_SALT,   null);
        String cajaA = keyPrefs.getString(KEY_CAJA_A, null);
        String cajaB = keyPrefs.getString(KEY_CAJA_B, null);
        String vault = loadLocalVault();
        if (salt == null || cajaA == null || vault == null) return null;
        return new UserData(salt, cajaA, cajaB, vault);
    }

    /**
     * Sube todos los datos locales a Firebase como copia de seguridad.
     * Se llama desde el botón "Exportar" en Ajustes.
     */
    public void exportToFirebase(Callback<Void> callback) {
        String salt  = keyPrefs.getString(KEY_SALT,   null);
        String cajaA = keyPrefs.getString(KEY_CAJA_A, null);
        String cajaB = keyPrefs.getString(KEY_CAJA_B, null);
        String vault = loadLocalVault();

        if (salt == null || cajaA == null || cajaB == null || vault == null) {
            callback.onError(new Exception("no_local_data"));
            return;
        }

        firebaseManager.uploadAll(salt, cajaA, cajaB, vault)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Descarga todos los datos de Firebase y los guarda localmente.
     * Se llama desde el botón "Importar" en Ajustes antes de descifrar.
     */
    public void downloadAndCacheFromFirebase(Callback<UserData> callback) {
        firebaseManager.downloadUserData()
                .addOnSuccessListener(userData -> {
                    if (userData != null) {
                        if (userData.vault != null) saveLocalVault(userData.vault);
                        if (userData.salt != null) {
                            keyPrefs.edit()
                                    .putString(KEY_SALT,   userData.salt)
                                    .putString(KEY_CAJA_A, userData.cajaA)
                                    .putString(KEY_CAJA_B, userData.cajaB)
                                    .apply();
                        }
                    }
                    callback.onSuccess(userData);
                })
                .addOnFailureListener(callback::onError);
    }

    public void uploadSpecificVaultToFirebase(String encryptedVault, Callback<Void> callback) {
        String salt  = keyPrefs.getString(KEY_SALT,   null);
        String cajaA = keyPrefs.getString(KEY_CAJA_A, null);
        String cajaB = keyPrefs.getString(KEY_CAJA_B, null);

        if (salt == null || cajaA == null || cajaB == null) {
            callback.onError(new Exception("no_local_data"));
            return;
        }

        firebaseManager.uploadAll(salt, cajaA, cajaB, encryptedVault)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void saveLocalVaultOnly(String encryptedVaultBase64) {
        saveLocalVault(encryptedVaultBase64);
    }

    /** true si hay un vault guardado en disco. */
    public boolean hasLocalVault() {
        return fileManager.vaultExists();
    }

    /** Borra el vault del disco. Llamar al cerrar sesión o borrar cuenta. */
    public void clearLocalVault() {
        fileManager.deleteVault();
    }

    // ── Borrado de cuenta ─────────────────────────────────────────────────────

    /**
     * Borra el documento de Firestore, la cuenta de Auth y el vault local.
     * Operación irreversible.
     */
    public void deleteAccount(Callback<Void> callback) {
        firebaseManager.deleteUserData()
                .addOnSuccessListener(unused ->
                        firebaseManager.deleteAuthAccount()
                                .addOnSuccessListener(v -> {
                                    clearLocalVault();
                                    callback.onSuccess(null);
                                })
                                .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    /** La única conversión Base64→byte[] del proyecto. Solo ocurre aquí para FileManager. */
    private void saveLocalVault(String encryptedVaultBase64) {
        try {
            byte[] bytes = Base64.decode(encryptedVaultBase64, Base64.NO_WRAP);
            fileManager.writeVault(bytes);
        } catch (Exception ignored) {}
    }

    private byte[] readLocalVaultBytes() {
        try {
            return fileManager.readVault();
        } catch (Exception e) {
            return null;
        }
    }
}