package com.example.clef.data.repository;

import android.content.Context;
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

    private final FileManager     fileManager;
    private final FirebaseManager firebaseManager;

    public VaultRepository(Context context) {
        this.fileManager     = new FileManager(context);
        this.firebaseManager = new FirebaseManager();
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
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    // ── Guardar bóveda ────────────────────────────────────────────────────────

    /**
     * Guarda el vault cifrado en disco y lo sube a Firebase.
     * Recibe el String Base64 que devuelve KeyManager.cifrarVault().
     */
    public void saveVault(String encryptedVaultBase64, Callback<Void> callback) {
        saveLocalVault(encryptedVaultBase64);
        firebaseManager.uploadVault(encryptedVaultBase64)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
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
                    if (userData != null && userData.vault != null) {
                        saveLocalVault(userData.vault);
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