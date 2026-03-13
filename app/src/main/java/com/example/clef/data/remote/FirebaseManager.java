package com.example.clef.data.remote;

// FirebaseManager es la capa de acceso a Firestore (base de datos en la nube).
// Sube y descarga los 4 documentos cifrados vinculados al usuario autenticado:
//   - Salt: bytes aleatorios usados en PBKDF2 para derivar las claves
//   - Caja A: la DEK cifrada con la Contraseña Maestra (login diario)
//   - Caja B: la DEK cifrada con el PUK (recuperación de emergencia)
//   - Bóveda: el vault.enc con todas las credenciales cifradas con la DEK
// Firebase solo ve blobs cifrados, nunca datos en claro (arquitectura Zero-Knowledge).
// Cada usuario tiene su propio documento en: /users/{uid}/

import android.util.Base64;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirebaseManager {

    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_SALT        = "salt";
    private static final String FIELD_CAJA_A      = "cajaA";
    private static final String FIELD_CAJA_B      = "cajaB";
    private static final String FIELD_VAULT        = "vault";

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public FirebaseManager() {
        this.db   = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    // Devuelve el UID del usuario autenticado actualmente
    private String getUid() {
        return auth.getCurrentUser().getUid();
    }

    // Referencia al documento del usuario en Firestore
    private com.google.firebase.firestore.DocumentReference userDoc() {
        return db.collection(COLLECTION_USERS).document(getUid());
    }

    // ── SUBIDA ────────────────────────────────────────────────────────────────

    // Sube Salt, Caja A y Caja B de una vez (se llama al registrarse)
    public Task<Void> uploadKeys(byte[] salt, byte[] cajaA, byte[] cajaB) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_SALT,   Base64.encodeToString(salt,   Base64.NO_WRAP));
        data.put(FIELD_CAJA_A, Base64.encodeToString(cajaA, Base64.NO_WRAP));
        data.put(FIELD_CAJA_B, Base64.encodeToString(cajaB, Base64.NO_WRAP));
        return userDoc().set(data);
    }

    // Sube el vault cifrado (se llama cada vez que se guarda una credencial)
    public Task<Void> uploadVault(byte[] encryptedVault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_VAULT, Base64.encodeToString(encryptedVault, Base64.NO_WRAP));
        return userDoc().update(data);
    }

    // Sobreescribe solo la Caja A (se llama al cambiar la Contraseña Maestra)
    public Task<Void> uploadCajaA(byte[] cajaA) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, Base64.encodeToString(cajaA, Base64.NO_WRAP));
        return userDoc().update(data);
    }

    // ── DESCARGA ──────────────────────────────────────────────────────────────

    // Descarga el documento del usuario completo
    public Task<DocumentSnapshot> downloadUserData() {
        return userDoc().get();
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    // Extrae un campo Base64 del DocumentSnapshot y lo devuelve como byte[]
    public static byte[] getBytes(DocumentSnapshot doc, String field) {
        String value = doc.getString(field);
        if (value == null) return null;
        return Base64.decode(value, Base64.NO_WRAP);
    }

    // Devuelve true si el usuario ya tiene datos en Firestore (ya se registró antes)
    public Task<Boolean> userExists() {
        return userDoc().get().continueWith(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot doc = task.getResult();
                return doc != null && doc.exists();
            }
            return false;
        });
    }
}
