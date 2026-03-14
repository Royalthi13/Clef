package com.example.clef.data.remote;

import android.util.Base64;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Esta clase se comunica con la base de datos en la nube (Firebase Firestore).
 *
 * Cada usuario tiene un documento en Firestore con 4 campos:
 *   - salt:  bytes aleatorios usados para derivar la clave con PBKDF2
 *   - cajaA: la DEK cifrada con la Contraseña Maestra (para el login diario)
 *   - cajaB: la DEK cifrada con el PUK (para recuperar la cuenta)
 *   - vault: el archivo con todas las contraseñas, cifrado con la DEK
 *
 * Los 4 campos se guardan en Base64 (texto), porque Firestore trabaja con texto,
 * no con bytes en bruto.
 *
 * Arquitectura Zero-Knowledge: Firebase NUNCA ve datos en claro.
 * Solo recibe y devuelve blobs cifrados que no puede leer.
 *
 * No usar directamente desde UI ni Crypto. Usar a través de VaultRepository.
 */
public class FirebaseManager {

    private static final String COLLECTION_USERS = "users";
    private static final String FIELD_SALT        = "salt";
    private static final String FIELD_CAJA_A      = "cajaA";
    private static final String FIELD_CAJA_B      = "cajaB";
    private static final String FIELD_VAULT        = "vault";

    private final FirebaseFirestore db;
    private final FirebaseAuth      auth;

    /**
     * Crea el FirebaseManager.
     * Obtiene automáticamente las instancias de Firestore y FirebaseAuth.
     */
    public FirebaseManager() {
        this.db   = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    // ── SUBIDA ────────────────────────────────────────────────────────────────

    /**
     * Sube el salt y las dos cajas (A y B) a Firestore. Solo se llama UNA VEZ,
     * cuando el usuario se registra por primera vez y crea su Contraseña Maestra.
     * Crea el documento del usuario en Firestore desde cero.
     *
     * @param salt  Bytes del salt generados aleatoriamente por CryptoUtils.
     * @param cajaA Bytes de la DEK cifrada con la Contraseña Maestra. Viene de CryptoUtils.
     * @param cajaB Bytes de la DEK cifrada con el PUK de emergencia. Viene de CryptoUtils.
     * @return      Task que avisa cuando Firebase confirma que se ha guardado.
     */
    public Task<Void> uploadKeys(byte[] salt, byte[] cajaA, byte[] cajaB) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_SALT,   Base64.encodeToString(salt,   Base64.NO_WRAP));
        data.put(FIELD_CAJA_A, Base64.encodeToString(cajaA, Base64.NO_WRAP));
        data.put(FIELD_CAJA_B, Base64.encodeToString(cajaB, Base64.NO_WRAP));
        return userDoc().set(data);
    }

    /**
     * Sube el vault cifrado a Firestore. Se llama cada vez que el usuario
     * añade, edita o borra una credencial.
     * El documento del usuario ya debe existir (creado antes con uploadKeys).
     *
     * @param encryptedVault Bytes del vault cifrado con AES-256-GCM. Viene de CryptoUtils.
     * @return               Task que avisa cuando Firebase confirma que se ha guardado.
     */
    public Task<Void> uploadVault(byte[] encryptedVault) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_VAULT, Base64.encodeToString(encryptedVault, Base64.NO_WRAP));
        return userDoc().update(data);
    }

    /**
     * Actualiza solo la cajaA en Firestore. Se llama cuando el usuario
     * cambia su Contraseña Maestra y hay que re-cifrar la DEK con la nueva clave.
     * No toca los otros campos (salt, cajaB, vault).
     *
     * @param cajaA Bytes de la DEK cifrada con la NUEVA Contraseña Maestra. Viene de CryptoUtils.
     * @return      Task que avisa cuando Firebase confirma que se ha actualizado.
     */
    public Task<Void> uploadCajaA(byte[] cajaA) {
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_CAJA_A, Base64.encodeToString(cajaA, Base64.NO_WRAP));
        return userDoc().update(data);
    }

    // ── DESCARGA ──────────────────────────────────────────────────────────────

    /**
     * Descarga el documento completo del usuario desde Firestore.
     * Contiene los 4 campos: salt, cajaA, cajaB y vault.
     * Para leer cada campo como bytes, usar el método getBytes() de abajo.
     *
     * @return Task que devuelve el DocumentSnapshot con todos los campos del usuario.
     */
    public Task<DocumentSnapshot> downloadUserData() {
        return userDoc().get();
    }

    // ── ESTADO ────────────────────────────────────────────────────────────────

    /**
     * Comprueba si el usuario ya tiene un documento en Firestore.
     * Se usa al iniciar sesión para saber si es un usuario nuevo (ir a registro)
     * o uno que ya existe (ir a desbloquear la bóveda).
     *
     * @return Task que devuelve true si el documento existe, false si es nuevo.
     */
    public Task<Boolean> userExists() {
        return userDoc().get().continueWith(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot doc = task.getResult();
                return doc != null && doc.exists();
            }
            return false;
        });
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    /**
     * Método de ayuda para leer un campo del documento descargado de Firestore.
     * Los campos están guardados en Base64 (texto). Este método los convierte
     * de vuelta a bytes para que CryptoUtils pueda trabajar con ellos.
     *
     * Ejemplo de uso:
     *   byte[] salt  = FirebaseManager.getBytes(doc, "salt");
     *   byte[] cajaA = FirebaseManager.getBytes(doc, "cajaA");
     *   byte[] vault = FirebaseManager.getBytes(doc, "vault");
     *
     * @param doc   El documento descargado con downloadUserData().
     * @param field El nombre del campo que quieres leer: "salt", "cajaA", "cajaB" o "vault".
     * @return      Los bytes del campo, o null si ese campo no existe en el documento.
     */
    public static byte[] getBytes(DocumentSnapshot doc, String field) {
        String value = doc.getString(field);
        if (value == null) return null;
        return Base64.decode(value, Base64.NO_WRAP);
    }

    // ── PRIVADO ───────────────────────────────────────────────────────────────

    /**
     * Devuelve el UID del usuario que tiene sesión iniciada en Firebase.
     * El UID es un identificador único que Firebase asigna a cada cuenta.
     *
     * @return UID del usuario actual como String.
     */
    private String getUid() {
        return auth.getCurrentUser().getUid();
    }

    /**
     * Devuelve la referencia al documento de este usuario en Firestore.
     * La ruta del documento es: /users/{uid}
     *
     * @return Referencia al documento del usuario actual.
     */
    private DocumentReference userDoc() {
        return db.collection(COLLECTION_USERS).document(getUid());
    }
}
