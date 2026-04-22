package com.example.clef.data.remote;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A-4 FIX: uploadAll() ahora incluye el campo "version" = 0 en el documento
 * inicial, evitando que las lecturas posteriores devuelvan version=0L por
 * ausencia del campo y generen falsos conflictos en uploadVaultVersioned().
 */
public class FirebaseManager {

    public static class UserData {
        public final String       salt;
        public final String       cajaA;
        public final String       cajaB;
        public final String       vault;
        public final long         version;
        public final List<String> knownIps;

        public UserData(String salt, String cajaA, String cajaB, String vault, long version,
                        List<String> knownIps) {
            this.salt     = salt;
            this.cajaA    = cajaA;
            this.cajaB    = cajaB;
            this.vault    = vault;
            this.version  = version;
            this.knownIps = knownIps != null ? knownIps : new ArrayList<>();
        }

        public boolean hasMasterPassword() {
            return cajaA != null && !cajaA.isEmpty();
        }
    }

    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_SALT        = "salt";
    private static final String FIELD_CAJA_A      = "cajaA";
    private static final String FIELD_CAJA_B      = "cajaB";
    private static final String FIELD_VAULT        = "vault";
    public  static final String FIELD_VERSION      = "version";
    public  static final String FIELD_KNOWN_IPS   = "knownIps";
    public  static final String CONFLICT_ERROR     = "vault_conflict";

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

    public void addVaultListener(OnVaultChangedListener listener) {
        removeVaultListener();
        vaultListener = userDoc().addSnapshotListener((snap, error) -> {
            if (error != null) { removeVaultListener(); return; }
            if (snap == null || !snap.exists()) return;
            String vault   = snap.getString(FIELD_VAULT);
            long   version = snap.contains(FIELD_VERSION) ? snap.getLong(FIELD_VERSION) : 0L;
            if (vault != null) listener.onVaultChanged(vault, version);
        });
    }

    public void removeVaultListener() {
        if (vaultListener != null) { vaultListener.remove(); vaultListener = null; }
    }

    // ── Subida ─────────────────────────────────────────────────────────────

    /**
     * A-4 FIX: incluye version=0 en el documento inicial para que las
     * operaciones versionadas posteriores funcionen correctamente.
     */
    /**
     * Sube los datos iniciales del usuario al documento de Firestore.
     * Usa SetOptions.merge() para no sobreescribir campos gestionados por otras
     * partes del sistema, como knownDevices y knownCountries que escribe la
     * Cloud Function checkLoginIp.
     */
    public Task<Void> uploadAll(String salt, String cajaA, String cajaB, String vault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_SALT,    salt);
        data.put(FIELD_CAJA_A,  cajaA);
        data.put(FIELD_CAJA_B,  cajaB);
        data.put(FIELD_VAULT,   vault);
        data.put(FIELD_VERSION, 0L);
        return userDoc().set(data, com.google.firebase.firestore.SetOptions.merge());
    }

    public Task<Void> uploadVault(String vault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_VAULT, vault);
        return userDoc().update(data);
    }

    public Task<Void> uploadVaultVersioned(String encryptedVault,
                                           long expectedVersion,
                                           long newVersion) {
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

    public Task<Void> uploadCajaA(String cajaA) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, cajaA);
        return userDoc().update(data);
    }

    public Task<Void> uploadCajaAyB(String cajaA, String cajaB) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, cajaA);
        data.put(FIELD_CAJA_B, cajaB);
        return userDoc().update(data);
    }

    // ── Descarga ──────────────────────────────────────────────────────────

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
            List<String> knownIps = (List<String>) doc.get(FIELD_KNOWN_IPS);
            return new UserData(
                    doc.getString(FIELD_SALT),
                    doc.getString(FIELD_CAJA_A),
                    doc.getString(FIELD_CAJA_B),
                    doc.getString(FIELD_VAULT),
                    version,
                    knownIps);
        });
    }

    // ── Estado ────────────────────────────────────────────────────────────

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

    // ── IPs conocidas ─────────────────────────────────────────────────────

    /**
     * Añade una IP a la lista de IPs conocidas del usuario.
     * Usa arrayUnion para no sobreescribir las existentes y evitar duplicados.
     */
    public Task<Void> saveKnownIp(String ip) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_KNOWN_IPS, com.google.firebase.firestore.FieldValue.arrayUnion(ip));
        return userDoc().update(data);
    }

    /**
     * Comprueba si una IP ya está en la lista de IPs conocidas del usuario.
     * Devuelve true si es conocida, false si es nueva.
     */
    public Task<Boolean> isKnownIp(String ip) {
        return userDoc().get().continueWith(task -> {
            if (!task.isSuccessful()) return false;
            DocumentSnapshot doc = task.getResult();
            if (doc == null || !doc.exists()) return false;
            List<String> knownIps = (List<String>) doc.get(FIELD_KNOWN_IPS);
            return knownIps != null && knownIps.contains(ip);
        });
    }

    // ── Borrado ───────────────────────────────────────────────────────────

    public Task<Void> deleteUserData()    { return userDoc().delete(); }
    public Task<Void> deleteAuthAccount() { return auth.getCurrentUser().delete(); }

    // ── Privado ───────────────────────────────────────────────────────────

    private String getUid() { return auth.getCurrentUser().getUid(); }
    private DocumentReference userDoc() {
        return db.collection(COLLECTION_USERS).document(getUid());
    }
}