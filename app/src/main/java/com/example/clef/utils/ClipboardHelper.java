package com.example.clef.utils;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;

public class ClipboardHelper {

    private static final long CLEAR_DELAY_MS     = 45_000;
    private static final long CLEAR_DELAY_PUK_MS = 300_000; // 5 minutos para el PUK

    // Timestamp y delay del ultimo contenido sensible copiado
    private static long copyTimestamp = 0;
    private static long copyDelay     = 0;

    private ClipboardHelper() {}

    /**
     * Copia el PUK al portapapeles con limpieza a los 5 minutos.
     */
    public static void copySensitivePuk(@NonNull Context context,
                                        @NonNull String label,
                                        @NonNull String text) {
        copySensitive(context, label, text, CLEAR_DELAY_PUK_MS);
    }

    /**
     * Copia texto sensible al portapapeles con limpieza a los 45 segundos.
     */
    public static void copySensitive(@NonNull Context context,
                                     @NonNull String label,
                                     @NonNull String text) {
        copySensitive(context, label, text, CLEAR_DELAY_MS);
    }

    private static void copySensitive(@NonNull Context context,
                                      @NonNull String label,
                                      @NonNull String text,
                                      long delayMs) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        ClipData clip = ClipData.newPlainText(label, text);

        // Android 13+: marcar como contenido sensible oculta la notificación del portapapeles
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }

        cm.setPrimaryClip(clip);

        // Guardar timestamp para limpiar cuando la app vuelva a foreground
        copyTimestamp = System.currentTimeMillis();
        copyDelay     = delayMs;
    }

    /**
     * Llamar desde onActivityResumed en ClefApp.
     * Si ha pasado el tiempo configurado, limpia el portapapeles.
     * La app esta en foreground en este momento, por lo que el sistema lo permite.
     */
    public static void clearIfExpired(@NonNull Context context) {
        if (copyTimestamp == 0) return;
        if (System.currentTimeMillis() - copyTimestamp < copyDelay) return;

        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip();
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""));
            }
        } catch (Exception ignored) {}

        copyTimestamp = 0;
        copyDelay     = 0;
    }
}
