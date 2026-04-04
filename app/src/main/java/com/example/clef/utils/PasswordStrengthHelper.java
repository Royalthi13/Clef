package com.example.clef.utils;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.clef.R;

public class PasswordStrengthHelper {

    public enum Strength { WEAK, MEDIUM, STRONG }

    public static Strength evaluate(String password) {
        if (password == null || password.isEmpty()) return Strength.WEAK;

        int score = 0;
        if (password.length() >= 8)  score++;
        if (password.length() >= 12) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;

        if (score <= 2) return Strength.WEAK;
        if (score <= 4) return Strength.MEDIUM;
        return Strength.STRONG;
    }

    /** Actualiza las 3 barras y la etiqueta según la contraseña dada. */
    public static void update(Context ctx, String password,
                              LinearLayout bar1, LinearLayout bar2, LinearLayout bar3,
                              TextView label) {
        Strength s = evaluate(password);

        int colorOff  = ContextCompat.getColor(ctx, R.color.clef_border);
        int colorWeak = ContextCompat.getColor(ctx, R.color.expiry_expired);   // rojo
        int colorMed  = ContextCompat.getColor(ctx, R.color.expiry_warning);   // naranja
        int colorOk   = ContextCompat.getColor(ctx, R.color.strength_strong);  // verde

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
