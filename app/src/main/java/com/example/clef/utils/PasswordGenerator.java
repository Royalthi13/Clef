package com.example.clef.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.clef.utils.SecurePrefs;

import java.security.SecureRandom;

/**
 * Genera contraseñas aleatorias criptográficamente seguras.
 *
 * La configuración (longitud, tipos de caracteres) se guarda en SharedPreferences
 * con la clave "generator_config" y la lee GeneratorFragment al cambiar cualquier opción.
 * AddItemDialog llama a generateFromPrefs() para rellenar el campo de contraseña.
 */
public class PasswordGenerator {
    private static final SecureRandom RNG = new SecureRandom();
    public static final String PREFS_NAME    = "generator_config";
    public static final String KEY_LENGTH    = "length";
    public static final String KEY_UPPERCASE = "upper";
    public static final String KEY_LOWERCASE = "lower";
    public static final String KEY_NUMBERS   = "numbers";
    public static final String KEY_SYMBOLS   = "symbols";

    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS   = "0123456789";
    private static final String SYMBOLS   = "!@#$%^&*()_+-=[]{}|;:,.<>?";

    private static final int DEFAULT_LENGTH = 16;

    private PasswordGenerator() {}

    /**
     * Genera una contraseña con los parámetros indicados.
     * Garantiza que al menos un carácter de cada tipo activado está presente.
     */
    public static String generate(int length, boolean upper,
                                  boolean lower, boolean numbers, boolean symbols) {
        if (length < 4) length = 4;

        StringBuilder charset = new StringBuilder();
        if (upper)   charset.append(UPPERCASE);
        if (lower)   charset.append(LOWERCASE);
        if (numbers) charset.append(NUMBERS);
        if (symbols) charset.append(SYMBOLS);
        if (charset.length() == 0) charset.append(LOWERCASE); // fallback


        char[] password = new char[length];

        // Insertar al menos un carácter de cada tipo activado (evita contraseñas sin números, etc.)
        int pos = 0;
        if (upper   && pos < length) password[pos++] = UPPERCASE.charAt(RNG.nextInt(UPPERCASE.length()));
        if (lower   && pos < length) password[pos++] = LOWERCASE.charAt(RNG.nextInt(LOWERCASE.length()));
        if (numbers && pos < length) password[pos++] = NUMBERS  .charAt(RNG.nextInt(NUMBERS.length()));
        if (symbols && pos < length) password[pos++] = SYMBOLS  .charAt(RNG.nextInt(SYMBOLS.length()));

        // Rellenar el resto aleatoriamente
        String charsetStr = charset.toString();
        for (int i = pos; i < length; i++) {
            password[i] = charsetStr.charAt(RNG.nextInt(charsetStr.length()));
        }

        // Mezclar para que los caracteres "garantizados" no estén siempre al principio
        for (int i = length - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }

        return new String(password);
    }

    /**
     * Lee la configuración guardada y genera una contraseña con ella.
     * Llamar desde AddItemDialog para rellenar el campo contraseña.
     */
    public static String generateFromPrefs(Context context) {
        SharedPreferences prefs = SecurePrefs.get(context, PREFS_NAME);
        int     length  = prefs.getInt(KEY_LENGTH,    DEFAULT_LENGTH);
        boolean upper   = prefs.getBoolean(KEY_UPPERCASE, true);
        boolean lower   = prefs.getBoolean(KEY_LOWERCASE, true);
        boolean numbers = prefs.getBoolean(KEY_NUMBERS,   true);
        boolean symbols = prefs.getBoolean(KEY_SYMBOLS,   false);
        return generate(length, upper, lower, numbers, symbols);
    }

    /** Guarda la configuración actual en SharedPreferences. */
    public static void saveConfig(Context context, int length, boolean upper,
                                  boolean lower, boolean numbers, boolean symbols) {
        SecurePrefs.get(context, PREFS_NAME)
                .edit()
                .putInt(KEY_LENGTH,       length)
                .putBoolean(KEY_UPPERCASE, upper)
                .putBoolean(KEY_LOWERCASE, lower)
                .putBoolean(KEY_NUMBERS,   numbers)
                .putBoolean(KEY_SYMBOLS,   symbols)
                .apply();
    }
}