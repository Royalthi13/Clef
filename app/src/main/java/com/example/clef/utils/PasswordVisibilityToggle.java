package com.example.clef.utils;

import android.text.InputType;
import android.widget.ImageButton;

import com.google.android.material.textfield.TextInputEditText;

import com.example.clef.R;

/**
 * Toggle persistente de visibilidad de contraseña.
 *
 * Antes: el campo solo mostraba la contraseña mientras el usuario mantenía
 * pulsado el botón ojo (onTouch). Ahora: un click alterna entre visible y oculto
 * y el campo mantiene el estado hasta un nuevo click.
 *
 * También cambia el icono entre "ver" y "no ver" para dar feedback visual.
 */
public final class PasswordVisibilityToggle {

    private PasswordVisibilityToggle() {}

    /**
     * Aplica el toggle al par (EditText, ImageButton).
     * El estado inicial se deriva del inputType actual del EditText.
     */
    public static void attach(TextInputEditText editText, ImageButton eyeButton) {
        refreshIcon(editText, eyeButton);
        eyeButton.setOnClickListener(v -> {
            boolean visible = isVisible(editText);
            int sel = editText.getText() != null ? editText.getText().length() : 0;
            editText.setInputType(InputType.TYPE_CLASS_TEXT |
                    (visible ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                            : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
            editText.setSelection(sel);
            refreshIcon(editText, eyeButton);
        });
    }

    private static boolean isVisible(TextInputEditText et) {
        int type = et.getInputType();
        return (type & InputType.TYPE_MASK_VARIATION) ==
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    }

    private static void refreshIcon(TextInputEditText et, ImageButton btn) {
        btn.setImageResource(isVisible(et)
                ? R.drawable.ic_visibility_off_24
                : R.drawable.ic_visibility_24);
    }
}