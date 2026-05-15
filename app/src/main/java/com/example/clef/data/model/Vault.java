package com.example.clef.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO que representa la bóveda de credenciales del usuario.
 *
 * Contiene la lista de credenciales y un número de versión para el control
 * de concurrencia optimista al sincronizar con Firestore.
 */
public class Vault {

    private List<Credential> credentials;
    private long version = 0;

    public Vault() {
        this.credentials = new ArrayList<>();
    }
    public List<Credential> getCredentials() { return credentials; }
    public void setCredentials(List<Credential> credentials) {
        this.credentials = credentials;
    }
    public void addCredential(Credential credential) {
        this.credentials.add(credential);
    }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}