package com.example.clef.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.example.clef.crypto.KeyManager;
import com.example.clef.data.local.FileManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.remote.FirebaseManager.UserData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class VaultRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    // Nombre base de las prefs — se añade el UID para aislar usuarios
    private static final String KEY_PREFS_BASE = "clef_key_cache";
    private static final String KEY_SALT        = "salt";
    private static final String KEY_CAJA_A      = "caja_a";
    private static final String KEY_CAJA_B      = "caja_b";
    // Campo que guarda el UID propietario del caché — para detección de cambio de cuenta
    private static final String KEY_OWNER_UID   = "owner_uid";

    private final Context         context;
    private final FileManager     fileManager;
    private final FirebaseManager firebaseManager;

    public VaultRepository(Context context) {
        this.fileManager     = new FileManager(context);
        this.firebaseManager = new FirebaseManager();
        this.context         = context;
    }

    // ── SharedPreferences con UID ──────────────────────────────────────────────

    /**
     * Devuelve el SharedPreferences específico del usuario actual.
     * Antes era siempre "clef_key_cache" → los datos de usuario A eran
     * visibles para usuario B en el mismo dispositivo.
     * Ahora cada usuario tiene su propio archivo de prefs.
     */
    private SharedPreferences getKeyPrefs() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String suffix = (u != null) ? "_" + u.getUid() : "_anon";
        return context.getSharedPreferences(KEY_PREFS_BASE + suffix, Context.MODE_PRIVATE);
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    public void registerUser(KeyManager.RegistrationBundle bundle, Callback<Void> callback) {
        firebaseManager.uploadAll(
                        bundle.saltBase64,
                        bundle.cajaABase64,
                        bundle.cajaBBase64,
                        bundle.bovedaCifradaBase64)
                .addOnSuccessListener(unused -> {
                    saveLocalVault(bundle.bovedaCifradaBase64);
                    cacheKeys(bundle.saltBase64, bundle.cajaABase64, bundle.cajaBBase64);
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    // ── Guardar bóveda ────────────────────────────────────────────────────────

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

    public void loadUserData(Callback<UserData> callback) {
        firebaseManager.downloadUserData()
                .addOnSuccessListener(userData -> {
                    if (userData != null) {
                        if (userData.vault != null) saveLocalVault(userData.vault);
                        if (userData.salt != null && userData.cajaA != null) {
                            cacheKeys(userData.salt, userData.cajaA, userData.cajaB);
                        }
                    }
                    callback.onSuccess(userData);
                })
                .addOnFailureListener(e ->
                        callback.onError(new Exception("offline_fallback", e)));
    }

    public String loadLocalVault() {
        byte[] bytes = readLocalVaultBytes();
        if (bytes == null) return null;
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    // ── Cambio de Contraseña Maestra ──────────────────────────────────────────

    public void updateCajaA(String nuevaCajaABase64, Callback<Void> callback) {
        firebaseManager.uploadCajaA(nuevaCajaABase64)
                .addOnSuccessListener(unused -> {
                    // Actualizar también el caché local
                    getKeyPrefs().edit().putString(KEY_CAJA_A, nuevaCajaABase64).apply();
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    public void userHasMasterPassword(Callback<Boolean> callback) {
        firebaseManager.userHasMasterPassword()
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    /**
     * Carga el caché local de claves.
     *
     * SEGURIDAD: Verifica que el UID guardado en el caché coincide con el
     * usuario actual antes de devolver datos. Si hay un cambio de cuenta,
     * devuelve null en lugar de entregar claves de otro usuario.
     */
    public UserData loadOfflineUserData() {
        SharedPreferences prefs = getKeyPrefs();

        // Verificación de propietario: el caché tiene el UID que lo creó
        String ownerUid = prefs.getString(KEY_OWNER_UID, null);
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) return null;
        if (ownerUid != null && !ownerUid.equals(current.getUid())) {
            // Caché de otro usuario — no usar
            return null;
        }

        String salt  = prefs.getString(KEY_SALT,   null);
        String cajaA = prefs.getString(KEY_CAJA_A, null);
        String cajaB = prefs.getString(KEY_CAJA_B, null);
        String vault = loadLocalVault();
        if (salt == null || cajaA == null || vault == null) return null;
        return new UserData(salt, cajaA, cajaB, vault);
    }

    public void exportToFirebase(Callback<Void> callback) {
        SharedPreferences prefs = getKeyPrefs();
        String salt  = prefs.getString(KEY_SALT,   null);
        String cajaA = prefs.getString(KEY_CAJA_A, null);
        String cajaB = prefs.getString(KEY_CAJA_B, null);
        String vault = loadLocalVault();

        if (salt == null || cajaA == null || cajaB == null || vault == null) {
            callback.onError(new Exception("no_local_data"));
            return;
        }

        firebaseManager.uploadAll(salt, cajaA, cajaB, vault)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public void downloadAndCacheFromFirebase(Callback<UserData> callback) {
        firebaseManager.downloadUserData()
                .addOnSuccessListener(userData -> {
                    if (userData != null) {
                        if (userData.vault != null) saveLocalVault(userData.vault);
                        if (userData.salt != null) {
                            cacheKeys(userData.salt, userData.cajaA, userData.cajaB);
                        }
                    }
                    callback.onSuccess(userData);
                })
                .addOnFailureListener(callback::onError);
    }

    public boolean hasLocalVault() { return fileManager.vaultExists(); }

    public void clearLocalVault() { fileManager.deleteVault(); }

    /**
     * Limpia el caché de claves del usuario actual.
     * Llamar al cerrar sesión para no dejar material criptográfico en disco.
     */
    public void clearKeyCache() {
        getKeyPrefs().edit().clear().apply();
    }

    // ── Borrado de cuenta ─────────────────────────────────────────────────────

    public void deleteAccount(Callback<Void> callback) {
        firebaseManager.deleteUserData()
                .addOnSuccessListener(unused ->
                        firebaseManager.deleteAuthAccount()
                                .addOnSuccessListener(v -> {
                                    clearLocalVault();
                                    clearKeyCache();
                                    callback.onSuccess(null);
                                })
                                .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    // ── Privado ───────────────────────────────────────────────────────────────

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

    /** Guarda salt, cajaA, cajaB y el UID del propietario en las prefs del usuario actual. */
    private void cacheKeys(String salt, String cajaA, String cajaB) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        SharedPreferences.Editor editor = getKeyPrefs().edit()
                .putString(KEY_SALT,   salt)
                .putString(KEY_CAJA_A, cajaA)
                .putString(KEY_CAJA_B, cajaB);
        if (u != null) {
            editor.putString(KEY_OWNER_UID, u.getUid());
        }
        editor.apply();
    }
}