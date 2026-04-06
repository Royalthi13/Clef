package com.example.clef.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.clef.crypto.KeyManager;
import com.example.clef.data.local.FileManager;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.remote.FirebaseManager.UserData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class VaultRepository {

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private static final String KEY_PREFS_BASE = "clef_key_cache";
    private static final String KEY_SALT        = "salt";
    private static final String KEY_CAJA_A      = "caja_a";
    private static final String KEY_CAJA_B      = "caja_b";
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

    private SharedPreferences getKeyPrefs() throws GeneralSecurityException, IOException {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        String suffix = (u != null) ? "_" + u.getUid() : "_anon";
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                context,
                KEY_PREFS_BASE + suffix,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
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
                    try {
                        cacheKeys(bundle.saltBase64, bundle.cajaABase64, bundle.cajaBBase64);
                    } catch (GeneralSecurityException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
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

    // ── Cargar datos ──────────────────────────────────────────────────────────

    public void loadUserData(Callback<UserData> callback) {
        firebaseManager.downloadUserData()
                .addOnSuccessListener(userData -> {
                    if (userData != null) {
                        if (userData.vault != null) saveLocalVault(userData.vault);
                        if (userData.salt != null && userData.cajaA != null) {
                            try {
                                cacheKeys(userData.salt, userData.cajaA, userData.cajaB);
                            } catch (GeneralSecurityException e) {
                                throw new RuntimeException(e);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
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

    // ── Cambio de Caja A (sin regenerar PUK) ──────────────────────────────────

    public void updateCajaA(String nuevaCajaABase64, Callback<Void> callback) {
        firebaseManager.uploadCajaA(nuevaCajaABase64)
                .addOnSuccessListener(unused -> {
                    try {
                        getKeyPrefs().edit().putString(KEY_CAJA_A, nuevaCajaABase64).apply();
                    } catch (GeneralSecurityException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    public void updateCajaAyB(String nuevaCajaABase64, String nuevaCajaBBase64, Callback<Void> callback) {
        firebaseManager.uploadCajaAyB(nuevaCajaABase64, nuevaCajaBBase64)
                .addOnSuccessListener(unused -> {
                    try {
                        getKeyPrefs().edit()
                                .putString(KEY_CAJA_A, nuevaCajaABase64)
                                .putString(KEY_CAJA_B, nuevaCajaBBase64)
                                .apply();
                    } catch (GeneralSecurityException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
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

    public UserData loadOfflineUserData() {
        SharedPreferences prefs = null;
        try {
            prefs = getKeyPrefs();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        String ownerUid = prefs.getString(KEY_OWNER_UID, null);
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) return null;
        if (ownerUid != null && !ownerUid.equals(current.getUid())) return null;

        String salt  = prefs.getString(KEY_SALT,   null);
        String cajaA = prefs.getString(KEY_CAJA_A, null);
        String cajaB = prefs.getString(KEY_CAJA_B, null);
        String vault = loadLocalVault();
        if (salt == null || cajaA == null || vault == null) return null;
        return new UserData(salt, cajaA, cajaB, vault, 0L);
    }

    public void exportToFirebase(Callback<Void> callback) {
        SharedPreferences prefs = null;
        try {
            prefs = getKeyPrefs();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
                            try {
                                cacheKeys(userData.salt, userData.cajaA, userData.cajaB);
                            } catch (GeneralSecurityException e) {
                                throw new RuntimeException(e);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    callback.onSuccess(userData);
                })
                .addOnFailureListener(callback::onError);
    }

    public boolean hasLocalVault() { return fileManager.vaultExists(); }

    public void saveLocalVaultOnly(String encryptedVaultBase64) {
        saveLocalVault(encryptedVaultBase64);
    }

    public void uploadSpecificVaultToFirebase(String encryptedVault, Callback<Void> callback) throws GeneralSecurityException, IOException {
        String salt  = getKeyPrefs().getString(KEY_SALT,   null);
        String cajaA = getKeyPrefs().getString(KEY_CAJA_A, null);
        String cajaB = getKeyPrefs().getString(KEY_CAJA_B, null);

        if (salt == null || cajaA == null || cajaB == null) {
            callback.onError(new Exception("no_local_data"));
            return;
        }

        firebaseManager.uploadAll(salt, cajaA, cajaB, encryptedVault)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Construye un vault solo con las credenciales synced=true, lo cifra y lo sube a Firebase.
     * El vault local completo NO se toca — llámalo después de saveLocalVaultOnly.
     */
    /**
     * Sube solo las credenciales synced=true a Firebase.
     * Usa una transacción versionada si expectedVersion >= 0.
     * Si la versión remota difiere → falla con FirebaseManager.CONFLICT_ERROR.
     */
    public void uploadSyncedOnly(Vault fullVault, byte[] dek, long expectedVersion, Callback<Void> callback) {
        try {
            Vault syncedVault = new Vault();
            for (Credential c : fullVault.getCredentials()) {
                if (c.isSynced()) syncedVault.addCredential(c);
            }
            String encryptedSynced = new KeyManager().cifrarVault(syncedVault, dek);

            if (expectedVersion >= 0) {
                new FirebaseManager()
                        .uploadVaultVersioned(encryptedSynced, expectedVersion, expectedVersion + 1)
                        .addOnSuccessListener(unused -> callback.onSuccess(null))
                        .addOnFailureListener(callback::onError);
            } else {
                uploadSpecificVaultToFirebase(encryptedSynced, callback);
            }
        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public void clearLocalVault() { fileManager.deleteVault(); }

    public void clearKeyCache() {
        try {
            getKeyPrefs().edit().clear().apply();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
                                .addOnFailureListener(e -> {
                                    android.util.Log.e("DeleteAccount", "deleteAuthAccount failed", e);
                                    callback.onError(e);
                                }))
                .addOnFailureListener(e -> {
                    android.util.Log.e("DeleteAccount", "deleteUserData failed", e);
                    callback.onError(e);
                });
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

    private void cacheKeys(String salt, String cajaA, String cajaB) throws GeneralSecurityException, IOException {
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