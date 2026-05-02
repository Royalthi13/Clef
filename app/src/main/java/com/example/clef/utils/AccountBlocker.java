package com.example.clef.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Gestiona el bloqueo permanente de cuentas tras agotar los intentos de recuperación con PUK.
 * El bloqueo persiste aunque el usuario cierre y reabra la app.
 * Para desbloquearlo se requiere intervención del servicio técnico.
 */
public final class AccountBlocker {

    private static final String TAG    = "AccountBlocker";
    private static final String PREFS  = "account_block";
    private static final String PREFIX = "blocked_";

    private AccountBlocker() {}

    /**
     * Bloquea la cuenta asociada al UID dado.
     * Llamar justo antes de cerrar sesión al agotar los intentos de PUK.
     */
    public static void block(Context context, String uid) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().putBoolean(PREFIX + uid, true).apply();
    }

    /**
     * Desbloquea la cuenta asociada al UID dado.
     * Reservado para uso futuro por parte del servicio técnico.
     */
    public static void unblock(Context context, String uid) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs != null) prefs.edit().remove(PREFIX + uid).apply();
    }

    /**
     * Devuelve true si la cuenta del UID dado está bloqueada.
     *
     * @param uid UID de Firebase del usuario. Si es null, devuelve false.
     */
    public static boolean isBlocked(Context context, String uid) {
        if (uid == null) return false;
        SharedPreferences prefs = getPrefs(context);
        return prefs != null && prefs.getBoolean(PREFIX + uid, false);
    }

    private static SharedPreferences getPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context, PREFS, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences no disponible; bloqueo no persistente.", e);
            return null;
        }
    }
}