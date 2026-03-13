package com.example.clef.data.model;

// Vault es el contenedor principal de todas las credenciales del usuario.
// Tiene una List<Credential> con todas las contraseñas guardadas.
// GSON serializa el Vault entero a un String JSON, que luego CryptoUtils cifra con AES-256-GCM.
// El resultado cifrado (bytes) es lo que se guarda en local (vault.enc) y se sube a Firestore.
// Al hacer login, se descifra el blob → JSON → Vault → List<Credential> en RAM.

import java.util.ArrayList;
import java.util.List;

public class Vault {

    private List<Credential> credentials;

    public Vault() {
        this.credentials = new ArrayList<>();
    }

    public List<Credential> getCredentials() {
        return credentials;
    }

    public void setCredentials(List<Credential> credentials) {
        this.credentials = credentials;
    }

    public void addCredential(Credential credential) {
        this.credentials.add(credential);
    }

    public void removeCredential(int index) {
        this.credentials.remove(index);
    }
}
