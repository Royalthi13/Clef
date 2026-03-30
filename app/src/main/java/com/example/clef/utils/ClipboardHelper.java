package com.example.clef.utils;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;

import androidx.annotation.NonNull;

public class ClipboardHelper {

    private static final long CLEAR_DELAY_MS = 45_000;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable pendingClear;

    private ClipboardHelper() {}

    /**
     * Copia texto al portapapeles y programa su borrado a los 45s.
     * En Android 13+ marcamos el clip como sensible para ocultar la preview.
     * El borrado real compara el texto exacto antes de limpiar,
     * para no borrar si el usuario copió algo distinto después.
     */
    public static void copySensitive(@NonNull Context context,
                                     @NonNull String label,
                                     @NonNull String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        ClipData clip = ClipData.newPlainText(label, text);

        // Android 13+: marcar como contenido sensible oculta la notificación de portapapeles
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }

        cm.setPrimaryClip(clip);

        // Cancelar borrado pendiente anterior
        if (pendingClear != null) {
            handler.removeCallbacks(pendingClear);
            pendingClear = null;
        }

        final String copiedText = text;

        pendingClear = () -> {
            try {
                if (!cm.hasPrimaryClip()) return;
                ClipData current = cm.getPrimaryClip();
                if (current == null || current.getItemCount() == 0) return;

                CharSequence currentText = current.getItemAt(0).coerceToText(context);
                if (currentText != null && copiedText.contentEquals(currentText)) {
                    cm.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            } catch (Exception ignored) {
                // SecurityException posible si la app está en background en algunos dispositivos
            }
        };

        handler.postDelayed(pendingClear, CLEAR_DELAY_MS);
    }
}