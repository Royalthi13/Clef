package com.example.clef.data.repository;

import android.content.Context;

import com.example.clef.data.local.FileManager;
import com.example.clef.data.remote.FirebaseManager;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Esta clase es la PUERTA DE ENTRADA a todos los datos de la bóveda.
 *
 * Es la única que deberían usar el equipo de Crypto y la UI.
 * Por dentro coordina dos cosas:
 *   - FileManager:     guarda el vault en el móvil (sin internet)
 *   - FirebaseManager: sube y baja el vault de la nube
 *
 * Reglas de funcionamiento:
 *   - Al GUARDAR: primero escribe en el móvil (instantáneo) y luego sube a Firebase.
 *   - Al CARGAR:  primero descarga de Firebase (datos frescos); si no hay internet,
 *                 usa lo que tiene guardado en el móvil.
 *   - Al REGISTRAR: sube salt + cajaA + cajaB + vault en orden y avisa al final.
 *
 * Ejemplo rápido de uso para el equipo Crypto:
 *
 *   VaultRepository repo = new VaultRepository(context);
 *
 *   // Guardar tras añadir una credencial:
 *   repo.saveVault(encryptedVault, new VaultRepository.Callback<Void>() {
 *       public void onSuccess(Void r) { ... }
 *       public void onError(Exception e) { ... }
 *   });
 */
public class VaultRepository {

    /**
     * Interfaz para recibir el resultado de cualquier operación del repositorio.
     * Como las operaciones son asíncronas (tardan un poco), usamos este callback
     * en lugar de devolver el resultado directamente.
     *
     * @param <T> El tipo de dato que devuelve la operación cuando tiene éxito.
     *            En operaciones de escritura es Void (no devuelve nada útil).
     */
    public interface Callback<T> {
        /**
         * Se llama cuando la operación termina correctamente.
         *
         * @param result El resultado. En escrituras (Callback<Void>) siempre es null.
         */
        void onSuccess(T result);

        /**
         * Se llama cuando la operación falla por cualquier motivo.
         *
         * @param e El error que ocurrió, con su mensaje y causa.
         */
        void onError(Exception e);
    }

    private final FileManager     fileManager;
    private final FirebaseManager firebaseManager;

    /**
     * Crea el VaultRepository.
     *
     * @param context Contexto de la app. Necesario para que FileManager
     *                pueda acceder a la carpeta interna del móvil.
     */
    public VaultRepository(Context context) {
        this.fileManager     = new FileManager(context);
        this.firebaseManager = new FirebaseManager();
    }

    // ── REGISTRO ──────────────────────────────────────────────────────────────

    /**
     * Registra al usuario subiendo todos sus datos cifrados a Firebase.
     * Se llama UNA SOLA VEZ, cuando el usuario crea su Contraseña Maestra.
     *
     * Pasos internos:
     *   1. Sube salt + cajaA + cajaB a Firebase.
     *   2. Sube el vault cifrado a Firebase.
     *   3. Guarda el vault en el móvil como caché.
     *   4. Llama a onSuccess.
     *
     * @param salt           Bytes del salt aleatorio. Generado por CryptoUtils.
     * @param cajaA          DEK cifrada con la Contraseña Maestra. Generada por CryptoUtils.
     * @param cajaB          DEK cifrada con el PUK de emergencia. Generada por CryptoUtils.
     * @param encryptedVault Vault cifrado con la DEK. Generado por CryptoUtils.
     * @param callback       onSuccess cuando todo se subió, onError si algo falló.
     */
    public void registerUser(byte[] salt, byte[] cajaA, byte[] cajaB,
                             byte[] encryptedVault, Callback<Void> callback) {
        firebaseManager.uploadKeys(salt, cajaA, cajaB)
                .addOnSuccessListener(unused ->
                        firebaseManager.uploadVault(encryptedVault)
                                .addOnSuccessListener(v -> {
                                    saveLocalVault(encryptedVault);
                                    callback.onSuccess(null);
                                })
                                .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    // ── GUARDAR BÓVEDA ────────────────────────────────────────────────────────

    /**
     * Guarda el vault cifrado en el móvil y lo sube a Firebase.
     * Se llama cada vez que el usuario añade, edita o borra una credencial.
     *
     * La escritura en el móvil es inmediata. La subida a Firebase es asíncrona:
     * el callback se dispara cuando Firebase confirma que lo recibió.
     *
     * @param encryptedVault Vault cifrado con AES-256-GCM. Lo prepara CryptoUtils.
     * @param callback       onSuccess cuando Firebase confirma, onError si hay fallo de red.
     */
    public void saveVault(byte[] encryptedVault, Callback<Void> callback) {
        saveLocalVault(encryptedVault);
        firebaseManager.uploadVault(encryptedVault)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    // ── CARGAR BÓVEDA ─────────────────────────────────────────────────────────

    /**
     * Descarga los datos del usuario desde Firebase para poder desbloquear la bóveda.
     *
     * Si la descarga va bien, actualiza automáticamente la caché del móvil.
     * Si no hay internet, llama a onError con el mensaje "offline_fallback".
     * En ese caso, usar loadLocalVault() para trabajar con los datos del móvil.
     *
     * Para leer los campos del resultado:
     *   byte[] salt  = FirebaseManager.getBytes(doc, "salt");
     *   byte[] cajaA = FirebaseManager.getBytes(doc, "cajaA");
     *   byte[] cajaB = FirebaseManager.getBytes(doc, "cajaB");
     *   byte[] vault = FirebaseManager.getBytes(doc, "vault");
     *
     * @param callback onSuccess con el DocumentSnapshot completo, o onError si falla.
     */
    public void loadUserData(Callback<DocumentSnapshot> callback) {
        firebaseManager.downloadUserData()
                .addOnSuccessListener(doc -> {
                    byte[] remoteVault = FirebaseManager.getBytes(doc, "vault");
                    if (remoteVault != null) {
                        saveLocalVault(remoteVault);
                    }
                    callback.onSuccess(doc);
                })
                .addOnFailureListener(e -> callback.onError(new Exception("offline_fallback", e)));
    }

    /**
     * Devuelve el vault cifrado que está guardado en el móvil.
     * Se usa como alternativa cuando no hay conexión a internet.
     *
     * @return Los bytes del vault cifrado, o null si nunca se ha guardado nada.
     */
    public byte[] loadLocalVault() {
        return readLocalVault();
    }

    // ── CAMBIO DE CONTRASEÑA MAESTRA ──────────────────────────────────────────

    /**
     * Actualiza la cajaA en Firebase cuando el usuario cambia su Contraseña Maestra.
     * CryptoUtils genera la nueva cajaA re-cifrando la DEK con la nueva contraseña.
     *
     * @param newCajaA DEK cifrada con la NUEVA Contraseña Maestra. Viene de CryptoUtils.
     * @param callback onSuccess cuando Firebase confirma, onError si hay fallo de red.
     */
    public void updateCajaA(byte[] newCajaA, Callback<Void> callback) {
        firebaseManager.uploadCajaA(newCajaA)
                .addOnSuccessListener(unused -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    // ── ESTADO ────────────────────────────────────────────────────────────────

    /**
     * Comprueba si el usuario ya se registró antes (tiene datos en Firebase).
     * La UI lo usa para decidir a qué pantalla ir tras el login con Google:
     *   - true  → ya existe → ir a desbloquear la bóveda
     *   - false → es nuevo  → ir a crear la Contraseña Maestra
     *
     * @param callback onSuccess con true/false, o onError si hay fallo de red.
     */
    public void userExists(Callback<Boolean> callback) {
        firebaseManager.userExists()
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    /**
     * Comprueba si hay un vault guardado en el móvil.
     *
     * @return true si existe el archivo vault.enc en el móvil, false si no.
     */
    public boolean hasLocalVault() {
        return fileManager.vaultExists();
    }

    /**
     * Borra el vault del móvil.
     * Llamar cuando el usuario cierre sesión o borre su cuenta,
     * para no dejar datos en el dispositivo.
     */
    public void clearLocalVault() {
        fileManager.deleteVault();
    }

    // ── PRIVADO ───────────────────────────────────────────────────────────────

    /** Guarda el vault en el móvil ignorando el error si falla (es solo caché). */
    private void saveLocalVault(byte[] encryptedVault) {
        try {
            fileManager.writeVault(encryptedVault);
        } catch (Exception ignored) {}
    }

    /** Lee el vault del móvil devolviendo null si falla o no existe. */
    private byte[] readLocalVault() {
        try {
            return fileManager.readVault();
        } catch (Exception e) {
            return null;
        }
    }
}
