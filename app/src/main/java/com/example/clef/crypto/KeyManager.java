package com.example.clef.crypto;

import android.util.Base64;

import com.example.clef.data.model.Vault;
import com.google.gson.Gson;

import java.util.Arrays;

/**
 * Orquesta la arquitectura Zero-Knowledge de Clef.
 *
 * Gestiona las tres piezas que se almacenan en Firebase:
 *   CAJA A  → DEK cifrada con KEK-Master (contraseña del usuario)
 *   CAJA B  → DEK cifrada con KEK-PUK    (código de emergencia)
 *   BÓVEDA  → JSON del Vault cifrado con la DEK
 *
 * No persiste nada por sí solo — solo transforma y devuelve resultados.
 * DEK = Data Encryption Key, KEK = Key Encryption Key.
 */
public class KeyManager {

    public static final int DEK_LENGTH_BYTES = 32;

    private final Gson gson = new Gson();

    public KeyManager() {}


    // ── DTOs de resultado ──────────────────────────────────────────────────────

    /** Todo lo necesario para subir a Firebase tras el registro. */
    public static class RegistrationBundle {
        public final String saltBase64;
        public final String cajaABase64;
        public final String cajaBBase64;
        public final String bovedaCifradaBase64;
        public final String puk;

        public RegistrationBundle(String saltBase64, String cajaABase64, String cajaBBase64,
                                  String bovedaCifradaBase64, String puk) {
            this.saltBase64          = saltBase64;
            this.cajaABase64         = cajaABase64;
            this.cajaBBase64         = cajaBBase64;
            this.bovedaCifradaBase64 = bovedaCifradaBase64;
            this.puk                 = puk;
        }
    }

    /** DEK activa y Vault descifrado tras un login correcto. */
    public static class LoginResult {
        public final byte[] dek;
        public final Vault vault;

        public LoginResult(byte[] dek, Vault vault) {
            this.dek   = dek;
            this.vault = vault;
        }
    }

    /** Nueva Caja A y DEK recuperada tras usar el PUK. */
    public static class RecoveryResult {
        public final String nuevaCajaABase64;
        public final byte[] dek;
        public final Vault vault;

        public RecoveryResult(String nuevaCajaABase64, byte[] dek, Vault vault) {
            this.nuevaCajaABase64 = nuevaCajaABase64;
            this.dek              = dek;
            this.vault            = vault;
        }
    }

    /**
     * FIX: Nuevo PUK + nueva Caja B generados tras la recuperación.
     * Necesario para invalidar completamente el PUK viejo.
     */
    public static class NuevoPukBundle {
        public final String cajaBBase64;
        public final String puk; // Nuevo PUK que se mostrará una sola vez

        public NuevoPukBundle(String cajaBBase64, String puk) {
            this.cajaBBase64 = cajaBBase64;
            this.puk         = puk;
        }
    }


    // ── Registro ───────────────────────────────────────────────────────────────

    /**
     * Genera Salt, DEK, PUK, Caja A, Caja B y Bóveda vacía para un usuario nuevo.
     * Llama a PBKDF2 dos veces (~800ms) — ejecutar siempre en hilo de fondo.
     */
    public RegistrationBundle register(char[] masterPassword) throws Exception {
        byte[] salt      = null;
        byte[] dek       = null;
        byte[] pukBytes  = null;
        byte[] kekMaster = null;
        byte[] kekPuk    = null;
        char[] pukChars  = null;

        try {
            salt     = CryptoUtils.generateSalt();
            dek      = CryptoUtils.generateRandomBytes(DEK_LENGTH_BYTES);
            pukBytes = CryptoUtils.generateRandomBytes(16);

            String pukFormateado = formatearPuk(pukBytes);
            pukChars = pukFormateado.toCharArray();

            kekMaster = CryptoUtils.deriveKey(masterPassword, salt);
            kekPuk    = CryptoUtils.deriveKey(pukChars, salt);

            String cajaA         = CryptoUtils.encrypt(dek, kekMaster);
            String cajaB         = CryptoUtils.encrypt(dek, kekPuk);
            String bovedaCifrada = CryptoUtils.encrypt(gson.toJson(new Vault()), dek);
            String saltBase64    = Base64.encodeToString(salt, Base64.NO_WRAP);

            return new RegistrationBundle(saltBase64, cajaA, cajaB, bovedaCifrada, pukFormateado);

        } finally {
            CryptoUtils.zeroise(salt);
            CryptoUtils.zeroise(dek);
            CryptoUtils.zeroise(pukBytes);
            CryptoUtils.zeroise(pukChars);
            CryptoUtils.zeroise(kekMaster);
            CryptoUtils.zeroise(kekPuk);
            CryptoUtils.zeroise(masterPassword);
        }
    }


    // ── Login ──────────────────────────────────────────────────────────────────

    /**
     * Abre la Caja A con la contraseña y descifra la Bóveda.
     * Si la contraseña es incorrecta, lanza AEADBadTagException.
     * Llama a PBKDF2 una vez (~400ms) — ejecutar siempre en hilo de fondo.
     */
    public LoginResult login(char[] masterPassword, String saltBase64,
                             String cajaABase64, String bovedaCifradaBase64) throws Exception {
        byte[] kekMaster = null;
        byte[] dek       = null;

        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            kekMaster   = CryptoUtils.deriveKey(masterPassword, salt);
            dek         = CryptoUtils.decrypt(cajaABase64, kekMaster);

            Vault vault = gson.fromJson(
                    CryptoUtils.decryptToString(bovedaCifradaBase64, dek), Vault.class);

            CryptoUtils.zeroise(kekMaster);
            CryptoUtils.zeroise(masterPassword);
            kekMaster = null;

            byte[] dekParaSesion = Arrays.copyOf(dek, dek.length);
            CryptoUtils.zeroise(dek);
            dek = null;

            return new LoginResult(dekParaSesion, vault);

        } finally {
            CryptoUtils.zeroise(kekMaster);
            CryptoUtils.zeroise(dek);
            CryptoUtils.zeroise(masterPassword);
        }
    }


    // ── Recuperación con PUK ───────────────────────────────────────────────────

    /**
     * Abre la Caja B con el PUK, descifra la Bóveda y genera una nueva Caja A.
     * Si el PUK es incorrecto, lanza AEADBadTagException.
     * Llama a PBKDF2 dos veces (~800ms) — ejecutar siempre en hilo de fondo.
     */
    public RecoveryResult recoverWithPuk(char[] pukChars, char[] nuevaContrasena,
                                         String saltBase64, String cajaBBase64,
                                         String bovedaCifradaBase64) throws Exception {
        byte[] kekPuk         = null;
        byte[] dek            = null;
        byte[] nuevaKekMaster = null;

        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);

            kekPuk = CryptoUtils.deriveKey(pukChars, salt);
            dek    = CryptoUtils.decrypt(cajaBBase64, kekPuk);

            Vault vault = gson.fromJson(
                    CryptoUtils.decryptToString(bovedaCifradaBase64, dek), Vault.class);

            nuevaKekMaster    = CryptoUtils.deriveKey(nuevaContrasena, salt);
            String nuevaCajaA = CryptoUtils.encrypt(dek, nuevaKekMaster);

            CryptoUtils.zeroise(kekPuk);
            CryptoUtils.zeroise(nuevaKekMaster);
            CryptoUtils.zeroise(pukChars);
            CryptoUtils.zeroise(nuevaContrasena);
            kekPuk = nuevaKekMaster = null;

            byte[] dekParaSesion = Arrays.copyOf(dek, dek.length);
            CryptoUtils.zeroise(dek);
            dek = null;

            return new RecoveryResult(nuevaCajaA, dekParaSesion, vault);

        } finally {
            CryptoUtils.zeroise(kekPuk);
            CryptoUtils.zeroise(dek);
            CryptoUtils.zeroise(nuevaKekMaster);
            CryptoUtils.zeroise(pukChars);
            CryptoUtils.zeroise(nuevaContrasena);
        }
    }

    /**
     * FIX: Genera un nuevo PUK y una nueva Caja B a partir de la DEK activa.
     *
     * Debe llamarse justo después de recoverWithPuk() para invalidar
     * completamente el PUK viejo. El PUK antiguo deja de funcionar
     * porque la nueva Caja B ya no está cifrada con él.
     *
     * @param dekActiva  La DEK ya descifrada (en claro, desde RecoveryResult).
     * @param saltBase64 El salt del usuario (mismo que se usó al registrarse).
     * @return NuevoPukBundle con la nueva Caja B y el nuevo PUK para mostrar al usuario.
     */
    public NuevoPukBundle generarNuevoCajaB(byte[] dekActiva, String saltBase64) throws Exception {
        byte[] salt      = null;
        byte[] pukBytes  = null;
        byte[] kekPuk    = null;
        char[] pukChars  = null;

        try {
            salt     = Base64.decode(saltBase64, Base64.NO_WRAP);
            pukBytes = CryptoUtils.generateRandomBytes(16);

            String pukFormateado = formatearPuk(pukBytes);
            pukChars = pukFormateado.toCharArray();

            kekPuk = CryptoUtils.deriveKey(pukChars, salt);
            String nuevaCajaB = CryptoUtils.encrypt(dekActiva, kekPuk);

            return new NuevoPukBundle(nuevaCajaB, pukFormateado);

        } finally {
            CryptoUtils.zeroise(salt);
            CryptoUtils.zeroise(pukBytes);
            CryptoUtils.zeroise(pukChars);
            CryptoUtils.zeroise(kekPuk);
            // dekActiva NO se zerisa aquí — el llamador la sigue necesitando para la sesión
        }
    }


    // ── Operaciones de Bóveda ──────────────────────────────────────────────────

    /** Serializa y cifra el Vault. Llamar tras cualquier cambio en las credenciales. */
    public String cifrarVault(Vault vault, byte[] dekActiva) throws Exception {
        return CryptoUtils.encrypt(gson.toJson(vault), dekActiva);
    }

    /** Descifra la Bóveda y reconstruye el Vault. */
    public Vault descifrarVault(String bovedaCifradaBase64, byte[] dekActiva) throws Exception {
        return gson.fromJson(
                CryptoUtils.decryptToString(bovedaCifradaBase64, dekActiva), Vault.class);
    }


    // ── Utilidades internas ────────────────────────────────────────────────────

    private static String formatearPuk(byte[] pukBytes) {
        StringBuilder hex = new StringBuilder(32);
        for (byte b : pukBytes) {
            hex.append(String.format("%02X", b));
        }
        String h = hex.toString();
        return h.substring(0, 4)  + "-" + h.substring(4, 8)   + "-" +
                h.substring(8, 12) + "-" + h.substring(12, 16) + "-" +
                h.substring(16, 20)+ "-" + h.substring(20, 24) + "-" +
                h.substring(24, 28)+ "-" + h.substring(28, 32);
    }
}