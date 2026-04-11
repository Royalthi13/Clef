package com.example.clef.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * M-7 FIX: Eliminado el modificador "transient" del campo version.
 * GSON no serializa transient, por lo que al deserializar siempre valía -1
 * y el campo cloudVaultVersion de SessionManager era la única fuente de verdad.
 * Ahora version se persiste en el JSON cifrado. Los vaults antiguos que no
 * tengan el campo recibirán 0 (valor por defecto de GSON para long), que
 * es equivalente al comportamiento anterior de firebase (version=0 si no existe).
 */
public class Vault {

    private List<Credential> credentials;
    private long version = 0; // M-7 FIX: ya no es transient; se serializa en JSON

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

    public void removeCredential(int index) {
        this.credentials.remove(index);
    }

    public long getVersion() { return version; }

    public void incrementVersion() { this.version++; }

    public void setVersion(long version) { this.version = version; }
}