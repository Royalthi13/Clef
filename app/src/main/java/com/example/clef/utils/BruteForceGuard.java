package com.example.clef.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Protege contra ataques de fuerza bruta con delays exponenciales.
 * Tras cada intento fallido aplica un delay creciente antes de permitir otro intento.
 * El contador se resetea solo cuando el usuario entra correctamente.
 *
 * Delays: intento 1=10s, 2=30s, 3=60s, 4=120s, 5=240s
 */
public class BruteForceGuard {

    private static final int[] DELAYS_SECONDS = {10, 30, 60, 120, 240};
    public  static final int   MAX_ATTEMPTS   = 5;

    private final SharedPreferences prefs;
    private final String            keyAttempts;
    private final String            keyLastAttempt;

    public BruteForceGuard(Context context, String uid, String prefix) {
        SharedPreferences p;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            p = EncryptedSharedPreferences.create(
                    context,
                    "brute_force_guard",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            p = context.getSharedPreferences("brute_force_guard", Context.MODE_PRIVATE);
        }
        prefs          = p;
        keyAttempts    = prefix + "_attempts_"     + uid;
        keyLastAttempt = prefix + "_last_attempt_" + uid;
    }

    /** Numero de intentos fallidos acumulados. */
    public int getAttemptCount() {
        return prefs.getInt(keyAttempts, 0);
    }

    /** Milisegundos que quedan de bloqueo. 0 si no hay bloqueo activo. */
    public long getRemainingLockoutMs() {
        int attempts = getAttemptCount();
        if (attempts == 0) return 0;
        int  delayIndex = Math.min(attempts - 1, DELAYS_SECONDS.length - 1);
        long delayMs    = DELAYS_SECONDS[delayIndex] * 1000L;
        long lastAttempt = prefs.getLong(keyLastAttempt, 0);
        long elapsed     = System.currentTimeMillis() - lastAttempt;
        return Math.max(0, delayMs - elapsed);
    }

    /** true si el usuario debe esperar antes de intentarlo de nuevo. */
    public boolean isLockedOut() {
        return getRemainingLockoutMs() > 0;
    }

    /** Llama esto cuando el intento falla. */
    public void recordFailure() {
        int attempts = getAttemptCount() + 1;
        prefs.edit()
                .putInt(keyAttempts,    attempts)
                .putLong(keyLastAttempt, System.currentTimeMillis())
                .apply();
    }

    /** Llama esto cuando el usuario entra correctamente. Resetea el contador. */
    public void recordSuccess() {
        prefs.edit()
                .remove(keyAttempts)
                .remove(keyLastAttempt)
                .apply();
    }

    /** Texto descriptivo del delay del siguiente intento (para mostrar al usuario). */
    public String getLockoutMessage() {
        long remaining = getRemainingLockoutMs();
        if (remaining <= 0) return "";
        long seconds = (remaining + 999) / 1000;
        return "Demasiados intentos. Espera " + seconds + "s.";
    }
}
