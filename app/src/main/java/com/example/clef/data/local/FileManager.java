package com.example.clef.data.local;

// FileManager gestiona la persistencia local del vault cifrado en el dispositivo.
// Lee y escribe el archivo "vault.enc" en el almacenamiento interno privado de la app
// (Context.getFilesDir()), que solo es accesible por la propia aplicación, nunca por otras apps.
// Guarda los bytes cifrados que vienen de CryptoUtils directamente, sin descifrarlos.
// Sirve como caché local para no depender siempre de conexión a Firestore.

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileManager {

    private static final String VAULT_FILE = "vault.enc";

    private final Context context;

    public FileManager(Context context) {
        this.context = context;
    }

    // Escribe los bytes cifrados del vault en vault.enc
    public void writeVault(byte[] encryptedVault) throws IOException {
        File file = new File(context.getFilesDir(), VAULT_FILE);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(encryptedVault);
        }
    }

    // Lee y devuelve los bytes cifrados de vault.enc
    public byte[] readVault() throws IOException {
        File file = new File(context.getFilesDir(), VAULT_FILE);
        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    // Devuelve true si existe el archivo vault.enc local
    public boolean vaultExists() {
        return new File(context.getFilesDir(), VAULT_FILE).exists();
    }

    // Borra el vault local (por ejemplo al cerrar sesión o borrar bóveda)
    public boolean deleteVault() {
        return new File(context.getFilesDir(), VAULT_FILE).delete();
    }
}
