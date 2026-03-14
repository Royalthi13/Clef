package com.example.clef.data.local;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Esta clase guarda y lee el vault cifrado en el propio móvil.
 *
 * El vault se guarda como un archivo llamado "vault.enc" en la memoria
 * interna del móvil. Android protege esa carpeta: ninguna otra app puede
 * acceder a ella, solo Clef.
 *
 * Su utilidad principal es hacer de CACHÉ: si el usuario no tiene internet,
 * puede seguir viendo sus contraseñas porque están guardadas aquí.
 *
 * IMPORTANTE: Esta clase nunca descifra nada. Solo guarda y lee los bytes
 * tal como los recibe de CryptoUtils.
 *
 * No usar directamente. Usar siempre a través de VaultRepository.
 */
public class FileManager {

    private static final String VAULT_FILE = "vault.enc";

    private final Context context;

    /**
     * Crea el FileManager.
     *
     * @param context Contexto de la app. Úsalo para acceder a la carpeta interna del móvil.
     */
    public FileManager(Context context) {
        this.context = context;
    }

    /**
     * Guarda el vault cifrado en el archivo vault.enc.
     * Si el archivo ya existía, lo sobreescribe entero.
     *
     * @param encryptedVault Los bytes del vault ya cifrado. Vienen de CryptoUtils.
     * @throws IOException Si hay algún problema al escribir en el almacenamiento.
     */
    public void writeVault(byte[] encryptedVault) throws IOException {
        File file = new File(context.getFilesDir(), VAULT_FILE);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(encryptedVault);
        }
    }

    /**
     * Lee el vault cifrado desde el archivo vault.enc y lo devuelve como bytes.
     * CryptoUtils se encargará de descifrarlo después.
     *
     * @return Los bytes del vault cifrado, o null si el archivo todavía no existe.
     * @throws IOException Si hay algún problema al leer el almacenamiento.
     */
    public byte[] readVault() throws IOException {
        File file = new File(context.getFilesDir(), VAULT_FILE);
        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    /**
     * Comprueba si ya existe un vault guardado en el móvil.
     * Útil para saber si el usuario ya se ha registrado antes.
     *
     * @return true si vault.enc existe, false si no.
     */
    public boolean vaultExists() {
        return new File(context.getFilesDir(), VAULT_FILE).exists();
    }

    /**
     * Borra el vault local del móvil.
     * Se llama cuando el usuario cierra sesión o borra su cuenta,
     * para que no queden datos en el dispositivo.
     *
     * @return true si se borró correctamente, false si no existía o hubo error.
     */
    public boolean deleteVault() {
        return new File(context.getFilesDir(), VAULT_FILE).delete();
    }
}
