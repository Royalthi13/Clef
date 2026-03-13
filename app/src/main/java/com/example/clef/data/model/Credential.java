package com.example.clef.data.model;

// Credential es el POJO (objeto de datos puro) que representa una contraseña guardada.
// Cada credencial tiene campos como: título (ej. "Gmail"), usuario, contraseña, URL y notas.
// No tiene lógica de negocio, solo datos. GSON la convierte a JSON para poder cifrarla.
// Ejemplo de una credencial en JSON antes de cifrar:
// { "title": "Gmail", "username": "pepe@gmail.com", "password": "abc123", "url": "...", "notes": "" }

public class Credential {

    private String email;
    private String username;
    private String password;
    private String url;
    private String notes;

    public Credential() {}

    public Credential(String email, String username, String password, String url, String notes) {
        this.email = email;
        this.username = username;
        this.password = password;
        this.url = url;
        this.notes = notes;
    }

    public String getEmail()    { return email; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getUrl()      { return url; }
    public String getNotes()    { return notes; }

    public void setEmail(String email)       { this.email = email; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setUrl(String url)           { this.url = url; }
    public void setNotes(String notes)       { this.notes = notes; }
}
