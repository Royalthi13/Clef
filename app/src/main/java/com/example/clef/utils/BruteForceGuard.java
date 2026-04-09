package com.example.clef.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Eliminado el fallback a SharedPreferences sin cifrar.
 * Si EncryptedSharedPreferences no está disponible (hardware sin TEE),
 * se usa un store temporal en memoria para la sesión actual.
 * Un atacante con ADB no podrá leer ni resetear los contadores desde disco.
 */
public class BruteForceGuard {

    private static final String TAG = "BruteForceGuard";
    private static final int[] DELAYS_SECONDS = {10, 30, 60, 120, 240};
    public  static final int   MAX_ATTEMPTS   = 5;

    private final SharedPreferences prefs;
    private final boolean           inMemoryFallback;
    private int   memAttempts    = 0;
    private long  memLastAttempt = 0;

    private final String keyAttempts;
    private final String keyLastAttempt;

    public BruteForceGuard(Context context, String uid, String prefix) {
        keyAttempts    = prefix + "_attempts_"     + uid;
        keyLastAttempt = prefix + "_last_attempt_" + uid;

        SharedPreferences p = null;
        boolean fallback = false;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            p = EncryptedSharedPreferences.create(
                    context, "brute_force_guard", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // A-1 FIX: NO caer a prefs sin cifrar. Usar solo memoria.
            Log.w(TAG, "EncryptedSharedPreferences no disponible; usando memoria temporal.", e);
            fallback = true;
        }
        prefs             = p;
        inMemoryFallback  = fallback;
    }

    public int getAttemptCount() {
        if (inMemoryFallback) return memAttempts;
        return prefs.getInt(keyAttempts, 0);
    }

    public long getRemainingLockoutMs() {
        int attempts = getAttemptCount();
        if (attempts == 0) return 0;
        int  delayIndex  = Math.min(attempts - 1, DELAYS_SECONDS.length - 1);
        long delayMs     = DELAYS_SECONDS[delayIndex] * 1000L;
        long lastAttempt = inMemoryFallback ? memLastAttempt
                : prefs.getLong(keyLastAttempt, 0);
        long elapsed     = System.currentTimeMillis() - lastAttempt;
        return Math.max(0, delayMs - elapsed);
    }

    public boolean isLockedOut() { return getRemainingLockoutMs() > 0; }

    public void recordFailure() {
        if (inMemoryFallback) {
            memAttempts++;
            memLastAttempt = System.currentTimeMillis();
        } else {
            prefs.edit()
                    .putInt(keyAttempts, getAttemptCount() + 1)
                    .putLong(keyLastAttempt, System.currentTimeMillis())
                    .apply();
        }
    }

    public void recordSuccess() {
        if (inMemoryFallback) {
            memAttempts    = 0;
            memLastAttempt = 0;
        } else {
            prefs.edit().remove(keyAttempts).remove(keyLastAttempt).apply();
        }
    }

    public String getLockoutMessage() {
        long remaining = getRemainingLockoutMs();
        if (remaining <= 0) return "";
        long seconds = (remaining + 999) / 1000;
        return "Demasiados intentos. Espera " + seconds + "s.";
    }
}