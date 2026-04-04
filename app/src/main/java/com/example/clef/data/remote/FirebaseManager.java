package com.example.clef.data.remote;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

/**
 * Comunicación con Firebase Firestore.
 *
 * Cada usuario tiene un documento en /users/{uid} con 4 campos en Base64:
 * salt, cajaA, cajaB, vault.
 */
public class FirebaseManager {

    // ── DTO ────────────────────────────────────────────────────────────────────

    public static class UserData {
        public final String salt;
        public final String cajaA;
        public final String cajaB;
        public final String vault;
        public final long   version;

        public UserData(String salt, String cajaA, String cajaB, String vault, long version) {
            this.salt    = salt;
            this.cajaA   = cajaA;
            this.cajaB   = cajaB;
            this.vault   = vault;
            this.version = version;
        }

        public boolean hasMasterPassword() {
            return cajaA != null && !cajaA.isEmpty();
        }
    }

    // ── Constantes ─────────────────────────────────────────────────────────────

    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_SALT        = "salt";
    private static final String FIELD_CAJA_A      = "cajaA";
    private static final String FIELD_CAJA_B      = "cajaB";
    private static final String FIELD_VAULT        = "vault";

    public interface OnVaultChangedListener {
        void onVaultChanged(String encryptedVault, long version);
    }

    private final FirebaseFirestore db;
    private final FirebaseAuth      auth;
    private ListenerRegistration    vaultListener;

    public FirebaseManager() {
        this.db   = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    /** Escucha cambios en tiempo real del vault en Firestore. */
    public void addVaultListener(OnVaultChangedListener listener) {
        removeVaultListener();
        vaultListener = userDoc().addSnapshotListener((snap, error) -> {
            if (error != null || snap == null || !snap.exists()) return;
            String vault = snap.getString(FIELD_VAULT);
            long version = snap.contains(FIELD_VERSION) ? snap.getLong(FIELD_VERSION) : 0L;
            if (vault != null) listener.onVaultChanged(vault, version);
        });
    }

    /** Elimina el listener activo. Llamar siempre al salir de la pantalla. */
    public void removeVaultListener() {
        if (vaultListener != null) {
            vaultListener.remove();
            vaultListener = null;
        }
    }

    // ── Subida ────────────────────────────────────────────────────────────────

    /** Crea el documento completo del usuario. Solo se llama al registrarse. */
    public Task<Void> uploadAll(String salt, String cajaA, String cajaB, String vault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_SALT,   salt);
        data.put(FIELD_CAJA_A, cajaA);
        data.put(FIELD_CAJA_B, cajaB);
        data.put(FIELD_VAULT,  vault);
        return userDoc().set(data);
    }

    public static final String CONFLICT_ERROR = "vault_conflict";
    private static final String FIELD_VERSION = "version";

    /** Actualiza solo el vault. Se llama cada vez que cambian las credenciales. */
    public Task<Void> uploadVault(String vault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_VAULT, vault);
        return userDoc().update(data);
    }

    /**
     * Sube el vault solo si la versión en Firebase coincide con expectedVersion.
     * Si otro dispositivo guardó antes, lanza una excepción con mensaje CONFLICT_ERROR.
     */
    public Task<Void> uploadVaultVersioned(String encryptedVault, long expectedVersion, long newVersion) {
        return db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(userDoc());
            long remoteVersion = snap.contains(FIELD_VERSION)
                    ? snap.getLong(FIELD_VERSION) : 0L;

            if (remoteVersion != expectedVersion) {
                throw new com.google.firebase.firestore.FirebaseFirestoreException(
                        CONFLICT_ERROR,
                        com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED);
            }

            Map<String, Object> data = new HashMap<>();
            data.put(FIELD_VAULT,   encryptedVault);
            data.put(FIELD_VERSION, newVersion);
            transaction.update(userDoc(), data);
            return null;
        });
    }

    /** Actualiza solo la cajaA. Se llama cuando el usuario cambia su Contraseña Maestra. */
    public Task<Void> uploadCajaA(String cajaA) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, cajaA);
        return userDoc().update(data);
    }

    /**
     * FIX: Actualiza cajaA y cajaB en la misma operación.
     *
     * Necesario tras recuperación con PUK para invalidar el PUK viejo
     * de forma atómica — nunca puede quedar un estado donde la Caja A
     * sea nueva pero la Caja B siga siendo la antigua.
     */
    public Task<Void> uploadCajaAyB(String cajaA, String cajaB) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, cajaA);
        data.put(FIELD_CAJA_B, cajaB);
        return userDoc().update(data);
    }

    // ── Descarga ──────────────────────────────────────────────────────────────

    public Task<UserData> downloadUserData() {
        return userDoc().get().continueWith(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                throw task.getException() != null
                        ? task.getException()
                        : new Exception("Error desconocido al descargar datos.");
            }
            DocumentSnapshot doc = task.getResult();
            if (!doc.exists()) return null;

            long version = doc.contains(FIELD_VERSION) ? doc.getLong(FIELD_VERSION) : 0L;
            return new UserData(
                    doc.getString(FIELD_SALT),
                    doc.getString(FIELD_CAJA_A),
                    doc.getString(FIELD_CAJA_B),
                    doc.getString(FIELD_VAULT),
                    version
            );
        });
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    public Task<Boolean> userHasMasterPassword() {
        return userDoc().get().continueWith(task -> {
            if (!task.isSuccessful()) return false;
            DocumentSnapshot doc = task.getResult();
            if (doc == null || !doc.exists()) return false;
            String cajaA = doc.getString(FIELD_CAJA_A);
            return cajaA != null && !cajaA.isEmpty();
        });
    }

    public Task<Boolean> userExists() {
        return userDoc().get().continueWith(task -> {
            if (!task.isSuccessful()) return false;
            DocumentSnapshot doc = task.getResult();
            return doc != null && doc.exists();
        });
    }

    // ── Borrado ───────────────────────────────────────────────────────────────

    public Task<Void> deleteUserData() {
        return userDoc().delete();
    }

    public Task<Void> deleteAuthAccount() {
        return auth.getCurrentUser().delete();
    }

    // ── Privado ───────────────────────────────────────────────────────────────

    private String getUid() {
        return auth.getCurrentUser().getUid();
    }

    private DocumentReference userDoc() {
        return db.collection(COLLECTION_USERS).document(getUid());
    }
}