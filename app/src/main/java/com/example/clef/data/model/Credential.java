package com.example.clef.data.model;

/**
 * Esta clase representa UNA contraseña guardada por el usuario.
 *
 * Piénsala como una ficha con 5 campos:
 *   - title:    el nombre del sitio, por ejemplo "Gmail" o "Instagram"
 *   - username: el nombre de usuario o email con el que entras
 *   - password: la contraseña de ese sitio
 *   - url:      la dirección web (opcional)
 *   - notes:    cualquier nota extra que quieras guardar (opcional)
 *
 * IMPORTANTE: Esta clase no hace nada con los datos, solo los guarda.
 * GSON la convierte a texto JSON para que CryptoUtils pueda cifrarla.
 *
 * Ejemplo de cómo se ve por dentro antes de cifrarse:
 *   { "title": "Gmail", "username": "pepe@gmail.com", "password": "abc123" }
 */
public class Credential {

    private String title;
    private String username;
    private String password;
    private String url;
    private String notes;

    /**
     * Constructor vacío necesario para que GSON pueda reconstruir
     * el objeto cuando descifra el JSON.
     */
    public Credential() {}

    /**
     * Crea una credencial con todos sus campos de golpe.
     *
     * @param title    Nombre del sitio, por ejemplo "Gmail".
     * @param username Usuario o email con el que entras al sitio.
     * @param password Contraseña del sitio.
     * @param url      Dirección web del sitio. Puede ser cadena vacía.
     * @param notes    Notas adicionales. Puede ser cadena vacía.
     */
    public Credential(String title, String username, String password, String url, String notes) {
        this.title    = title;
        this.username = username;
        this.password = password;
        this.url      = url;
        this.notes    = notes;
    }

    /** Devuelve el nombre del sitio, por ejemplo "Gmail". */
    public String getTitle()    { return title; }

    /** Devuelve el nombre de usuario o email. */
    public String getUsername() { return username; }

    /** Devuelve la contraseña del sitio. */
    public String getPassword() { return password; }

    /** Devuelve la URL del sitio web. Puede ser null o vacío. */
    public String getUrl()      { return url; }

    /** Devuelve las notas adicionales. Puede ser null o vacío. */
    public String getNotes()    { return notes; }

    /** Cambia el nombre del sitio. */
    public void setTitle(String title)       { this.title = title; }

    /** Cambia el nombre de usuario. */
    public void setUsername(String username) { this.username = username; }

    /** Cambia la contraseña. */
    public void setPassword(String password) { this.password = password; }

    /** Cambia la URL. */
    public void setUrl(String url)           { this.url = url; }

    /** Cambia las notas. */
    public void setNotes(String notes)       { this.notes = notes; }
}
