package com.example.clef.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

public class ClipboardHelper {

    private static final long CLEAR_DELAY_MS = 45_000;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable pendingClear;

    private ClipboardHelper() {}

    /**
     * Copia texto al portapapeles y programa su borrado a los 45s.
     * Si el usuario cambia el portapapeles antes, no borra el contenido nuevo.
     */
    public static void copySensitive(@NonNull Context context,
                                     @NonNull String label,
                                     @NonNull String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) return;

        ClipData clip = ClipData.newPlainText(label, text);
        cm.setPrimaryClip(clip);

        if (pendingClear != null) {
            handler.removeCallbacks(pendingClear);
        }

        pendingClear = () -> {
            ClipData current = cm.getPrimaryClip();
            if (current != null
                    && current.getItemCount() > 0
                    && current.getDescription() != null
                    && "text/plain".equals(current.getDescription().getMimeType(0))) {
                CharSequence currentText = current.getItemAt(0).coerceToText(context);
                if (currentText != null && currentText.toString().equals(text)) {
                    cm.setPrimaryClip(ClipData.newPlainText("", ""));
                }
            }
        };

        handler.postDelayed(pendingClear, CLEAR_DELAY_MS);
    }
}
