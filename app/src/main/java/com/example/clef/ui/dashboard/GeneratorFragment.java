package com.example.clef.ui.dashboard;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.clef.R;
import com.example.clef.utils.ClipboardHelper;
import com.example.clef.utils.PasswordGenerator;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

/**
 * Fragmento del generador de contraseñas.
 *
 * FIX UX: El fragment original ocultaba los botones "Generar" y "Copiar" dejando
 * una card con "TU NUEVA CONTRASEÑA" sin funcionalidad, confundiendo al usuario.
 *
 * Ahora el fragment tiene doble función coherente:
 *   1. Genera y muestra una contraseña de vista previa en tiempo real.
 *   2. Permite configurar los parámetros que se usarán en AddItemDialog.
 *
 * Así el usuario ve inmediatamente el efecto de sus ajustes y puede copiar
 * la contraseña generada si lo desea.
 */
public class GeneratorFragment extends Fragment {

    private TextView       tvGeneratedPassword;
    private TextView       tvLengthValue;
    private Slider         sliderLength;
    private MaterialSwitch switchUppercase;
    private MaterialSwitch switchLowercase;
    private MaterialSwitch switchNumbers;
    private MaterialSwitch switchSymbols;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_generator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvGeneratedPassword = view.findViewById(R.id.tvGeneratedPassword);
        tvLengthValue       = view.findViewById(R.id.tvLengthValue);
        sliderLength        = view.findViewById(R.id.sliderLength);
        switchUppercase     = view.findViewById(R.id.switchUppercase);
        switchLowercase     = view.findViewById(R.id.switchLowercase);
        switchNumbers       = view.findViewById(R.id.switchNumbers);
        switchSymbols       = view.findViewById(R.id.switchSymbols);

        // FIX: Mostrar y conectar los botones — ya no se ocultan
        View btnCopy     = view.findViewById(R.id.btnCopy);
        View btnGenerate = view.findViewById(R.id.btnGenerate);

        if (btnCopy != null) {
            btnCopy.setVisibility(View.VISIBLE);
            btnCopy.setOnClickListener(v -> {
                String pwd = tvGeneratedPassword.getText() != null
                        ? tvGeneratedPassword.getText().toString() : "";
                if (!pwd.isEmpty()) {
                    ClipboardHelper.copySensitive(requireContext(), "Contraseña generada", pwd);
                    com.google.android.material.snackbar.Snackbar
                            .make(view, "Contraseña copiada (se borrará en 45s)",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                            .show();
                }
            });
        }

        if (btnGenerate != null) {
            btnGenerate.setVisibility(View.VISIBLE);
            btnGenerate.setOnClickListener(v -> regenerate());
        }

        // Cargar config y generar una contraseña inicial
        loadSavedConfig();
        updateLengthLabel((int) sliderLength.getValue());
        regenerate();

        sliderLength.addOnChangeListener((slider, value, fromUser) -> {
            updateLengthLabel((int) value);
            saveConfig();
            regenerate(); // Vista previa en tiempo real
        });

        switchUppercase.setOnCheckedChangeListener((btn, checked) -> { saveConfig(); regenerate(); });
        switchLowercase.setOnCheckedChangeListener((btn, checked) -> { saveConfig(); regenerate(); });
        switchNumbers  .setOnCheckedChangeListener((btn, checked) -> { saveConfig(); regenerate(); });
        switchSymbols  .setOnCheckedChangeListener((btn, checked) -> { saveConfig(); regenerate(); });
    }

    private void regenerate() {
        String pwd = PasswordGenerator.generateFromPrefs(requireContext());
        if (tvGeneratedPassword != null) {
            tvGeneratedPassword.setText(pwd);
        }
    }

    private void updateLengthLabel(int length) {
        tvLengthValue.setText(getString(R.string.generador_tvlength, length));
    }

    private void saveConfig() {
        PasswordGenerator.saveConfig(
                requireContext(),
                (int) sliderLength.getValue(),
                switchUppercase.isChecked(),
                switchLowercase.isChecked(),
                switchNumbers.isChecked(),
                switchSymbols.isChecked()
        );
    }

    private void loadSavedConfig() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PasswordGenerator.PREFS_NAME,
                        android.content.Context.MODE_PRIVATE);
        sliderLength   .setValue(  prefs.getInt    (PasswordGenerator.KEY_LENGTH,    16));
        switchUppercase.setChecked(prefs.getBoolean(PasswordGenerator.KEY_UPPERCASE, true));
        switchLowercase.setChecked(prefs.getBoolean(PasswordGenerator.KEY_LOWERCASE, true));
        switchNumbers  .setChecked(prefs.getBoolean(PasswordGenerator.KEY_NUMBERS,   true));
        switchSymbols  .setChecked(prefs.getBoolean(PasswordGenerator.KEY_SYMBOLS,   false));
    }
}