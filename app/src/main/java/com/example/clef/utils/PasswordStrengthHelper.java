package com.example.clef.utils;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.clef.R;

/**
 * M-4 FIX: evaluate() ahora itera los chars una sola vez en lugar de
 * compilar y ejecutar 4 regex distintas por cada pulsación de tecla.
 * Mismo enfoque que CreateMasterActivity ya usaba en su Strength enum.
 */
public class PasswordStrengthHelper {

    public enum Strength { WEAK, MEDIUM, STRONG }

    public static Strength evaluate(String password) {
        if (password == null || password.isEmpty()) return Strength.WEAK;

        int     len    = password.length();
        boolean lower  = false, upper = false, digit = false, symbol = false;

        for (int i = 0; i < len; i++) {
            char c = password.charAt(i);
            if      (c >= 'a' && c <= 'z') lower  = true;
            else if (c >= 'A' && c <= 'Z') upper  = true;
            else if (c >= '0' && c <= '9') digit  = true;
            else                           symbol = true;
            if (lower && upper && digit && symbol) break;
        }

        int score = 0;
        if (len >= 8)  score++;
        if (len >= 12) score++;
        if (upper)     score++;
        if (lower)     score++;
        if (digit)     score++;
        if (symbol)    score++;

        if (score <= 2) return Strength.WEAK;
        if (score <= 4) return Strength.MEDIUM;
        return Strength.STRONG;
    }

    public static void update(Context ctx, String password,
                              LinearLayout bar1, LinearLayout bar2, LinearLayout bar3,
                              TextView label) {
        Strength s = evaluate(password);

        int colorOff  = ContextCompat.getColor(ctx, R.color.clef_border);
        int colorWeak = ContextCompat.getColor(ctx, R.color.expiry_expired);
        int colorMed  = ContextCompat.getColor(ctx, R.color.expiry_warning);
        int colorOk   = ContextCompat.getColor(ctx, R.color.strength_strong);

        switch (s) {
            case WEAK:
                bar1.setBackgroundColor(colorWeak);
                bar2.setBackgroundColor(colorOff);
                bar3.setBackgroundColor(colorOff);
                label.setText("Débil");
                label.setTextColor(colorWeak);
                break;
            case MEDIUM:
                bar1.setBackgroundColor(colorMed);
                bar2.setBackgroundColor(colorMed);
                bar3.setBackgroundColor(colorOff);
                label.setText("Media");
                label.setTextColor(colorMed);
                break;
            case STRONG:
                bar1.setBackgroundColor(colorOk);
                bar2.setBackgroundColor(colorOk);
                bar3.setBackgroundColor(colorOk);
                label.setText("Fuerte");
                label.setTextColor(colorOk);
                break;
        }
    }
}