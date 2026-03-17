package com.example.clef.data.remote;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Comunicación con Firebase Firestore.
 *
 * Cada usuario tiene un documento en /users/{uid} con 4 campos en Base64:
 * salt, cajaA, cajaB, vault.
 *
 * Esta clase es la única que conoce los nombres de esos campos.
 * El resto del proyecto trabaja con el DTO UserData, no con DocumentSnapshot.
 * No usar directamente desde UI. Usar a través de VaultRepository.
 */
public class FirebaseManager {

    // ── DTO de datos del usuario ───────────────────────────────────────────────

    /**
     * Objeto tipado con los datos del usuario descargados de Firestore.
     * Evita que las capas superiores dependan de los nombres de campo internos.
     */
    public static class UserData {
        public final String salt;
        public final String cajaA;
        public final String cajaB;
        public final String vault;

        UserData(String salt, String cajaA, String cajaB, String vault) {
            this.salt  = salt;
            this.cajaA = cajaA;
            this.cajaB = cajaB;
            this.vault = vault;
        }

        /** true si el usuario tiene Contraseña Maestra configurada. */
        public boolean hasMasterPassword() {
            return cajaA != null && !cajaA.isEmpty();
        }
    }

    // ── Constantes internas ────────────────────────────────────────────────────

    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_SALT        = "salt";
    private static final String FIELD_CAJA_A      = "cajaA";
    private static final String FIELD_CAJA_B      = "cajaB";
    private static final String FIELD_VAULT        = "vault";

    private final FirebaseFirestore db;
    private final FirebaseAuth      auth;

    public FirebaseManager() {
        this.db   = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    // ── Subida ────────────────────────────────────────────────────────────────

    /**
     * Crea el documento del usuario con todos los campos en una sola escritura.
     * Solo se llama una vez, en el registro.
     */
    public Task<Void> uploadAll(String salt, String cajaA, String cajaB, String vault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_SALT,   salt);
        data.put(FIELD_CAJA_A, cajaA);
        data.put(FIELD_CAJA_B, cajaB);
        data.put(FIELD_VAULT,  vault);
        return userDoc().set(data);
    }

    /** Actualiza solo el vault. Se llama cada vez que cambian las credenciales. */
    public Task<Void> uploadVault(String vault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_VAULT, vault);
        return userDoc().update(data);
    }

    /** Actualiza solo la cajaA. Se llama cuando el usuario cambia su Contraseña Maestra. */
    public Task<Void> uploadCajaA(String cajaA) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, cajaA);
        return userDoc().update(data);
    }

    // ── Descarga ──────────────────────────────────────────────────────────────

    /**
     * Descarga el documento del usuario y lo devuelve como UserData tipado.
     * Si el documento no existe, devuelve null dentro del Task.
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

            return new UserData(
                    doc.getString(FIELD_SALT),
                    doc.getString(FIELD_CAJA_A),
                    doc.getString(FIELD_CAJA_B),
                    doc.getString(FIELD_VAULT)
            );
        });
    }

    // ── Estado ────────────────────────────────────────────────────────────────

    /**
     * Comprueba si el usuario ya tiene Contraseña Maestra configurada.
     * Verifica que cajaA existe y no está vacía — más fiable que solo
     * comprobar si el documento existe.
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

    // ── Borrado ───────────────────────────────────────────────────────────────

    /** Borra el documento del usuario en Firestore. */
    public Task<Void> deleteUserData() {
        return userDoc().delete();
    }

    /** Elimina la cuenta de Firebase Auth. Llamar después de deleteUserData(). */
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