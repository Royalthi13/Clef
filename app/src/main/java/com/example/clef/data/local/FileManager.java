package com.example.clef.data.local;

import android.content.Context;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Lee y escribe el vault cifrado en la memoria interna del dispositivo.
 *
 * SEGURIDAD: El archivo se nombra "vault_<uid>.enc" para que cada usuario
 * del mismo dispositivo tenga su propio fichero. Antes existía un único
 * "vault.enc" compartido que podía filtrar datos entre cuentas.
 *
 * Android protege la carpeta filesDir: ninguna otra app puede acceder.
 * Esta clase nunca descifra nada — solo guarda y lee bytes opacos.
 *
 * No usar directamente. Usar siempre a través de VaultRepository.
 */
public class FileManager {

    private static final String VAULT_FILE_PREFIX = "vault_";
    private static final String VAULT_FILE_SUFFIX = ".enc";

    // Nombre del fichero legacy (pre-UID) para migración silenciosa
    private static final String VAULT_FILE_LEGACY = "vault.enc";

    private final Context context;

    public FileManager(Context context) {
        this.context = context;
    }

    // ── Nombre de fichero por UID ──────────────────────────────────────────────

    /**
     * Devuelve el nombre de fichero del vault para el usuario actual.
     * Si no hay usuario autenticado (caso raro), usa "vault_anon.enc".
     */
    private String getVaultFileName() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (user != null) ? user.getUid() : "anon";
        return VAULT_FILE_PREFIX + uid + VAULT_FILE_SUFFIX;
    }

    private File getVaultFile() {
        return new File(context.getFilesDir(), getVaultFileName());
    }

    // ── API pública ────────────────────────────────────────────────────────────

    /**
     * Guarda el vault cifrado. Sobreescribe si ya existía.
     *
     * @param encryptedVault Bytes del vault ya cifrado con AES-256-GCM.
     * @throws IOException Si hay algún problema al escribir.
     */
    public void writeVault(byte[] encryptedVault) throws IOException {
        File file = getVaultFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(encryptedVault);
        }
    }

    /**
     * Lee el vault cifrado desde disco.
     *
     * Migración silenciosa: si no existe el fichero con UID pero sí el
     * legacy "vault.enc", lo renombra para no perder datos de usuarios
     * existentes que actualizan la app.
     *
     * @return Bytes del vault cifrado, o null si no existe ningún vault.
     * @throws IOException Si hay problemas al leer.
     */
    public byte[] readVault() throws IOException {
        File file = getVaultFile();

        // Migración: vault.enc → vault_<uid>.enc
        if (!file.exists()) {
            File legacy = new File(context.getFilesDir(), VAULT_FILE_LEGACY);
            if (legacy.exists()) {
                //noinspection ResultOfMethodCallIgnored
                legacy.renameTo(file);
            }
        }

        if (!file.exists()) return null;

        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    /**
     * Comprueba si existe un vault guardado para el usuario actual.
     */
    public boolean vaultExists() {
        return getVaultFile().exists();
    }

    /**
     * Borra el vault del usuario actual del dispositivo.
     * Se llama al cerrar sesión o borrar cuenta.
     *
     * @return true si se borró, false si no existía o hubo error.
     */
    public boolean deleteVault() {
        return getVaultFile().delete();
    }
}