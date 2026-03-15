package com.example.clef.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Gestiona el tema de la app (claro, oscuro, sistema).
 * Guarda la preferencia en SharedPreferences y la aplica con AppCompatDelegate.
 */
public class ThemeManager {

    public static final int MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    public static final int MODE_LIGHT  = AppCompatDelegate.MODE_NIGHT_NO;
    public static final int MODE_DARK   = AppCompatDelegate.MODE_NIGHT_YES;

    private static final String PREFS_NAME = "clef_prefs";
    private static final String KEY_THEME  = "theme_mode";

    /** Aplica el tema guardado. Llamar desde Application.onCreate(). */
    public static void applyStored(Context context) {
        int mode = load(context);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** Guarda y aplica inmediatamente el modo elegido. */
    public static void apply(Context context, int mode) {
        save(context, mode);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    /** Devuelve el modo guardado (por defecto: sistema). */
    public static int load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_THEME, MODE_SYSTEM);
    }

    private static void save(Context context, int mode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME, mode)
                .apply();
    }
}
