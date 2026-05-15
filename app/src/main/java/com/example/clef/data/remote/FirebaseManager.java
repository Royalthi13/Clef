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
 * Capa de acceso a datos remota que encapsula todas las operaciones contra
 * Cloud Firestore y Firebase Authentication.
 *
 * <p>Cada usuario tiene un único documento en la colección {@code users},
 * identificado por su UID de Firebase Auth. El documento almacena únicamente
 * datos cifrados (salt, cajaA, cajaB, vault), por lo que Firebase nunca recibe
 * información en texto claro — arquitectura zero-knowledge.
 *
 * <p>Los campos {@code knownDevices} y {@code knownCountries} son escritos
 * exclusivamente por la Cloud Function {@code checkLoginIp} y nunca se leen
 * ni escriben desde la app Android.
 */
public class FirebaseManager {

    // ── DTO ────────────────────────────────────────────────────────────────

    /**
     * DTO inmutable con los datos cifrados del usuario descargados de Firestore.
     * Todos los campos de texto son blobs opacos en Base64; la app los descifra
     * localmente y Firebase nunca tiene acceso a la información en claro.
     */
    public static class UserData {
        /** Salt aleatorio de 32 bytes (Base64) usado en PBKDF2 para derivar la KEK. */
        public final String salt;
        /** DEK cifrada con la KEK derivada de la contraseña maestra (Base64). */
        public final String cajaA;
        /** DEK cifrada con la KEK derivada del PUK de recuperación (Base64). */
        public final String cajaB;
        /** Bóveda de credenciales cifrada con la DEK (Base64). */
        public final String vault;
        /** Versión optimista del vault para detectar conflictos de escritura concurrente. */
        public final long   version;

        public UserData(String salt, String cajaA, String cajaB, String vault, long version) {
            this.salt    = salt;
            this.cajaA   = cajaA;
            this.cajaB   = cajaB;
            this.vault   = vault;
            this.version = version;
        }

        /** @return {@code true} si el usuario ya tiene configurada una contraseña maestra. */
        public boolean hasMasterPassword() {
            return cajaA != null && !cajaA.isEmpty();
        }
    }

    // ── Constantes ─────────────────────────────────────────────────────────

    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_SALT        = "salt";
    private static final String FIELD_CAJA_A      = "cajaA";
    private static final String FIELD_CAJA_B      = "cajaB";
    private static final String FIELD_VAULT        = "vault";
    /** Nombre del campo de versión optimista en Firestore. Público para uso en transacciones externas. */
    public  static final String FIELD_VERSION      = "version";
    /** Código de error devuelto cuando hay un conflicto de versión al escribir el vault. */
    public  static final String CONFLICT_ERROR     = "vault_conflict";

    // ── Interfaz de escucha ────────────────────────────────────────────────

    /** Callback que recibe el vault actualizado cada vez que cambia en Firestore en tiempo real. */
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

    // ── Listener en tiempo real ────────────────────────────────────────────

    /**
     * Registra un listener en tiempo real sobre el documento del usuario.
     * Notifica al callback cada vez que el vault cambia en Firestore,
     * por ejemplo, cuando el usuario guarda desde otro dispositivo.
     * Reemplaza cualquier listener previo antes de registrar el nuevo.
     *
     * @param listener receptor de los cambios del vault.
     */
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

    /**
     * Elimina el listener en tiempo real activo, si existe.
     * Debe llamarse en {@code onStop()} o {@code onDestroy()} para evitar fugas de memoria.
     */
    public void removeVaultListener() {
        if (vaultListener != null) { vaultListener.remove(); vaultListener = null; }
    }

    // ── Subida ─────────────────────────────────────────────────────────────

    /**
     * Sube los datos iniciales del usuario al documento de Firestore.
     * Incluye {@code version=0} para que las operaciones versionadas posteriores
     * funcionen correctamente. Usa {@code SetOptions.merge()} para no sobreescribir
     * campos gestionados por la Cloud Function ({@code knownDevices}, {@code knownCountries}).
     *
     * @param salt  salt PBKDF2 del usuario en Base64.
     * @param cajaA DEK cifrada con la KEK-Master en Base64.
     * @param cajaB DEK cifrada con la KEK-PUK en Base64.
     * @param vault bóveda inicial cifrada en Base64.
     * @return Task que completa cuando Firestore confirma la escritura.
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

    /**
     * Actualiza únicamente el vault cifrado sin modificar otros campos del documento.
     * Para escrituras con garantía de no sobreescribir cambios concurrentes, usar
     * {@link #uploadVaultVersioned(String, long, long)}.
     *
     * @param vault nuevo vault cifrado en Base64.
     */
    public Task<Void> uploadVault(String vault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_VAULT, vault);
        return userDoc().update(data);
    }

    /**
     * Actualiza el vault con control optimista de concurrencia mediante transacción Firestore.
     * Compara {@code expectedVersion} con la versión almacenada en remoto; si no coinciden,
     * aborta con el código {@link #CONFLICT_ERROR} para que el llamador resuelva el conflicto
     * antes de reintentar.
     *
     * @param encryptedVault  nuevo vault cifrado en Base64.
     * @param expectedVersion versión que el cliente cree tener actualmente.
     * @param newVersion      versión que se almacenará si la transacción tiene éxito.
     * @return Task que completa si no hay conflicto, o falla con ABORTED si lo hay.
     */
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

    /**
     * Actualiza únicamente {@code cajaA} (DEK re-cifrada con la nueva KEK-Master).
     * Se invoca al cambiar la contraseña maestra para que los datos sigan siendo
     * accesibles con la nueva contraseña.
     *
     * @param cajaA nueva cajaA cifrada en Base64.
     */
    public Task<Void> uploadCajaA(String cajaA) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, cajaA);
        return userDoc().update(data);
    }

    /**
     * Actualiza {@code cajaA} y {@code cajaB} en una sola escritura atómica.
     * Se usa tras regenerar el PUK para mantener ambas cajas sincronizadas
     * con la misma DEK subyacente.
     *
     * @param cajaA nueva cajaA en Base64.
     * @param cajaB nueva cajaB en Base64.
     */
    public Task<Void> uploadCajaAyB(String cajaA, String cajaB) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, cajaA);
        data.put(FIELD_CAJA_B, cajaB);
        return userDoc().update(data);
    }

    // ── Descarga ──────────────────────────────────────────────────────────

    /**
     * Descarga el documento completo del usuario desde Firestore y lo empaqueta
     * en un {@link UserData}.
     *
     * @return Task con un {@link UserData}, o {@code null} si el documento no existe.
     *         La Task falla si Firestore no está disponible o la autenticación ha expirado.
     */
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
                    version);
        });
    }

    // ── Consultas de estado ────────────────────────────────────────────────

    /**
     * Comprueba si el usuario ya tiene contraseña maestra configurada
     * verificando que el campo {@code cajaA} existe y no está vacío.
     *
     * @return Task con {@code true} si el usuario tiene contraseña maestra establecida.
     */
    public Task<Boolean> userHasMasterPassword() {
        return userDoc().get().continueWith(task -> {
            if (!task.isSuccessful()) return false;
            DocumentSnapshot doc = task.getResult();
            if (doc == null || !doc.exists()) return false;
            String cajaA = doc.getString(FIELD_CAJA_A);
            return cajaA != null && !cajaA.isEmpty();
        });
    }

    /**
     * Comprueba si el documento del usuario ya existe en Firestore.
     * Útil para distinguir un primer registro de un login con datos previos.
     *
     * @return Task con {@code true} si el documento existe.
     */
    public Task<Boolean> userExists() {
        return userDoc().get().continueWith(task -> {
            if (!task.isSuccessful()) return false;
            DocumentSnapshot doc = task.getResult();
            return doc != null && doc.exists();
        });
    }

    // ── Borrado ───────────────────────────────────────────────────────────

    /** Elimina el documento Firestore del usuario. Operación irreversible. */
    public Task<Void> deleteUserData() { return userDoc().delete(); }

    /** Elimina la cuenta de Firebase Authentication del usuario autenticado actualmente. */
    public Task<Void> deleteAuthAccount() { return auth.getCurrentUser().delete(); }

    // ── Privado ───────────────────────────────────────────────────────────

    /**
     * @return UID del usuario autenticado actualmente.
     * @throws IllegalStateException si se llama sin sesión activa.
     */
    private String getUid() {
        if (auth.getCurrentUser() == null) {
            throw new IllegalStateException("No hay usuario autenticado.");
        }
        return auth.getCurrentUser().getUid();
    }

    /** @return referencia al documento Firestore del usuario autenticado. */
    private DocumentReference userDoc() {
        return db.collection(COLLECTION_USERS).document(getUid());
    }
}
