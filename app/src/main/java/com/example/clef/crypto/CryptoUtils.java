package com.example.clef.crypto;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Motor criptográfico de bajo nivel de Clef.
 * Solo maneja primitivas: derivar clave, cifrar y descifrar.
 * La lógica de negocio (Cajas, Bóveda) vive en KeyManager.
 */
public class CryptoUtils {

    private static final String AES_ALGORITHM      = "AES";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int    GCM_TAG_BITS       = 128;
    private static final int    GCM_IV_BYTES       = 12;
    private static final String KDF_ALGORITHM      = "PBKDF2WithHmacSHA256";
    private static final int    PBKDF2_ITERATIONS  = 230_000;
    private static final int    KEY_LENGTH_BITS    = 256;
    public  static final int    SALT_BYTES         = 32;

    // SecureRandom es thread-safe, se reutiliza en lugar de instanciar uno nuevo por llamada
    private static final SecureRandom RNG = new SecureRandom();

    private CryptoUtils() {}


    /**
     * Deriva una clave AES-256 a partir de una contraseña y un Salt usando PBKDF2.
     * Tarda ~400ms por diseño — llamar siempre desde un hilo de fondo.
     * Se usa char[] para poder zerizar la contraseña tras derivar.
     */
    public static byte[] deriveKey(char[] password, byte[] salt) throws Exception {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("La contraseña no puede ser nula o vacía.");
        }
        if (salt == null || salt.length != SALT_BYTES) {
            throw new IllegalArgumentException(
                    "Salt inválido: se esperaban " + SALT_BYTES + " bytes, " +
                            "llegaron " + (salt == null ? "null" : salt.length) + "."
            );
        }

        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }


    /**
     * Cifra bytes con AES-256-GCM. Formato del resultado: [IV (12B)][ciphertext+tag].
     * El IV es aleatorio en cada llamada — nunca se reutiliza.
     */
    public static String encrypt(byte[] plaintext, byte[] keyBytes) throws Exception {
        validateKey(keyBytes);

        byte[] iv = generateRandomBytes(GCM_IV_BYTES);

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE,
                new SecretKeySpec(keyBytes, AES_ALGORITHM),
                new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv,         0, combined, 0,         iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    /** Sobrecarga de conveniencia para cifrar un String directamente. */
    public static String encrypt(String plaintext, byte[] keyBytes) throws Exception {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8), keyBytes);
    }


    /**
     * Descifra un blob generado por encrypt().
     * Lanza AEADBadTagException si la clave es incorrecta o los datos fueron manipulados.
     */
    public static byte[] decrypt(String encryptedBase64, byte[] keyBytes) throws Exception {
        validateKey(keyBytes);

        byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);

        // Mínimo: IV (12B) + GCM tag (16B) + 1B de datos reales
        final int MIN_LENGTH = GCM_IV_BYTES + (GCM_TAG_BITS / 8) + 1;
        if (combined.length < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Blob cifrado inválido: " + combined.length + " bytes (mínimo " + MIN_LENGTH + ")."
            );
        }

        byte[] iv         = Arrays.copyOfRange(combined, 0, GCM_IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_BYTES, combined.length);

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE,
                new SecretKeySpec(keyBytes, AES_ALGORITHM),
                new GCMParameterSpec(GCM_TAG_BITS, iv));

        return cipher.doFinal(ciphertext);
    }

    /** Sobrecarga de conveniencia que devuelve el resultado como String UTF-8. */
    public static String decryptToString(String encryptedBase64, byte[] keyBytes) throws Exception {
        return new String(decrypt(encryptedBase64, keyBytes), StandardCharsets.UTF_8);
    }


    /** Genera bytes aleatorios criptográficamente seguros con SecureRandom. */
    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        RNG.nextBytes(bytes);
        return bytes;
    }

    /** Genera un Salt de SALT_BYTES bytes para un nuevo usuario. */
    public static byte[] generateSalt() {
        return generateRandomBytes(SALT_BYTES);
    }


    /** Sobrescribe un array de bytes con ceros para limpiar claves de la RAM. */
    public static void zeroise(byte[] sensitiveBytes) {
        if (sensitiveBytes != null) {
            Arrays.fill(sensitiveBytes, (byte) 0x00);
        }
    }

    /** Sobrescribe un array de chars con ceros para limpiar contraseñas de la RAM. */
    public static void zeroise(char[] sensitiveChars) {
        if (sensitiveChars != null) {
            Arrays.fill(sensitiveChars, '\u0000');
        }
    }


    /**
     * Valida que la clave tenga exactamente 32 bytes (AES-256).
     * SecretKeySpec acepta cualquier tamaño sin quejarse; esta validación falla antes.
     */
    private static void validateKey(byte[] keyBytes) {
        final int EXPECTED = KEY_LENGTH_BITS / 8;
        if (keyBytes == null || keyBytes.length != EXPECTED) {
            throw new IllegalArgumentException(
                    "Clave AES-256 inválida: se esperaban " + EXPECTED +
                            " bytes, llegaron " + (keyBytes == null ? "null" : keyBytes.length) + "."
            );
        }
    }
}