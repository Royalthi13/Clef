package com.example.clef.utils;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;

/**
 * M-5 FIX: Sustituidos los campos estáticos simples (copyTimestamp, copyDelay)
 * por un par de variables que almacenan el timestamp y delay del último clip.
 * La semántica anterior era correcta para un único clip a la vez, pero al copiar
 * el PUK después de una contraseña el timestamp de la contraseña se perdía.
 * Ahora se almacena solo el delay más corto pendiente para garantizar que
 * cualquier clip sensible se limpie en el menor tiempo configurado.
 */
public class ClipboardHelper {

    private static final long CLEAR_DELAY_MS     = 45_000;
    private static final long CLEAR_DELAY_PUK_MS = 300_000;

    // M-5 FIX: guardamos la expiración absoluta (epoch ms) del clip más urgente.
    private static volatile long earliestExpiry = 0;

    private ClipboardHelper() {}

    public static void copySensitivePuk(@NonNull Context context,
                                        @NonNull String label,
                                        @NonNull String text) {
        copySensitive(context, label, text, CLEAR_DELAY_PUK_MS);
    }

    public static void copySensitive(@NonNull Context context,
                                     @NonNull String label,
                                     @NonNull String text) {
        copySensitive(context, label, text, CLEAR_DELAY_MS);
    }

    private static void copySensitive(@NonNull Context context,
                                      @NonNull String label,
                                      @NonNull String text,
                                      long delayMs) {
        ClipboardManager cm =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        ClipData clip = ClipData.newPlainText(label, text);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }

        cm.setPrimaryClip(clip);

        // M-5 FIX: registrar la expiración más próxima para que clearIfExpired
        // limpie en el menor tiempo posible cuando haya varios clips activos.
        long expiry = System.currentTimeMillis() + delayMs;
        if (earliestExpiry == 0 || expiry < earliestExpiry) {
            earliestExpiry = expiry;
        }
    }

    public static void clearIfExpired(@NonNull Context context) {
        if (earliestExpiry == 0) return;
        if (System.currentTimeMillis() < earliestExpiry) return;

        ClipboardManager cm =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip();
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""));
            }
        } catch (Exception ignored) {}

        earliestExpiry = 0;
    }
}