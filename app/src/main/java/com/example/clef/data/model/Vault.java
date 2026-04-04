package com.example.clef.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Esta clase es el CONTENEDOR de todas las contraseñas del usuario.
 *
 * Piénsala como una caja fuerte que dentro tiene una lista de fichas (Credential).
 * Cuando el usuario guarda una contraseña nueva, se añade a esa lista.
 *
 * El flujo completo es:
 *   1. El Vault tiene una List con todas las Credentials.
 *   2. GSON convierte ese Vault entero a un texto JSON.
 *   3. CryptoUtils cifra ese JSON con AES-256 y lo convierte en bytes incomprensibles.
 *   4. Esos bytes son los que se guardan en Firebase y en el móvil.
 *
 * Al cargar, el proceso es al revés: bytes → descifrar → JSON → Vault → List<Credential>.
 */
public class Vault {

    private List<Credential> credentials;
    private transient long version = -1; // -1 = desconocido (no cargado de Firebase aún)

    /**
     * Crea un Vault vacío con una lista de credenciales también vacía.
     * Se usa al registrar al usuario por primera vez.
     */
    public Vault() {
        this.credentials = new ArrayList<>();
    }

    /**
     * Devuelve la lista completa de credenciales guardadas.
     * La UI usa esta lista para mostrarlas en pantalla.
     *
     * @return Lista de credenciales. Nunca es null, puede estar vacía.
     */
    public List<Credential> getCredentials() {
        return credentials;
    }

    /**
     * Reemplaza toda la lista de credenciales.
     * Se usa cuando GSON reconstruye el Vault desde el JSON descifrado.
     *
     * @param credentials Nueva lista de credenciales.
     */
    public void setCredentials(List<Credential> credentials) {
        this.credentials = credentials;
    }

    /**
     * Añade una credencial nueva al final de la lista.
     * Después de llamar a esto hay que cifrar y guardar el Vault otra vez.
     *
     * @param credential La credencial nueva a añadir.
     */
    public void addCredential(Credential credential) {
        this.credentials.add(credential);
    }

    /**
     * Elimina la credencial en la posición indicada.
     * Después de llamar a esto hay que cifrar y guardar el Vault otra vez.
     *
     * @param index Posición de la credencial a eliminar (empieza en 0).
     */
    public void removeCredential(int index) {
        this.credentials.remove(index);
    }

    public long getVersion() { return version; }

    /** Incrementa la versión antes de cada subida a Firebase. */
    public void incrementVersion() { this.version++; }
}
