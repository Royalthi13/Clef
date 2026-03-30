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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

/**
 * Fragmento de configuración del generador de contraseñas.
 *
 * No genera la contraseña final aquí — eso ocurre en AddItemDialog al pulsar
 * el icono de dado en el campo de contraseña. Este fragment solo guarda la
 * configuración (longitud y tipos de caracteres) que PasswordGenerator.generateFromPrefs()
 * leerá después.
 *
 * El botón "Generar" de este fragment sirve como preview para que el usuario
 * pueda ver cómo quedaría una contraseña con la configuración actual.
 */
public class GeneratorFragment extends Fragment {

    private TextView       tvGeneratedPassword;
    private TextView       tvLengthValue;
    private Slider         sliderLength;
    private MaterialSwitch switchUppercase;
    private MaterialSwitch switchLowercase;
    private MaterialSwitch switchNumbers;
    private MaterialSwitch switchSymbols;
    private MaterialButton btnCopy;
    private MaterialButton btnGenerate;

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
        btnCopy             = view.findViewById(R.id.btnCopy);
        btnGenerate         = view.findViewById(R.id.btnGenerate);

        loadSavedConfig();
        updateLengthLabel((int) sliderLength.getValue());
        generatePreview();

        sliderLength.addOnChangeListener((slider, value, fromUser) -> {
            updateLengthLabel((int) value);
            saveConfig();
            generatePreview();
        });

        switchUppercase.setOnCheckedChangeListener((btn, checked) -> { saveConfig(); generatePreview(); });
        switchLowercase.setOnCheckedChangeListener((btn, checked) -> { saveConfig(); generatePreview(); });
        switchNumbers  .setOnCheckedChangeListener((btn, checked) -> { saveConfig(); generatePreview(); });
        switchSymbols  .setOnCheckedChangeListener((btn, checked) -> { saveConfig(); generatePreview(); });

        btnGenerate.setOnClickListener(v -> generatePreview());

        btnCopy.setOnClickListener(v -> {
            String pwd = tvGeneratedPassword.getText().toString();
            if (!pwd.isEmpty() && !pwd.equals(getString(R.string.generator_new_password))) {
                ClipboardHelper.copySensitive(requireContext(), "contraseña", pwd);
            }
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void generatePreview() {
        String pwd = PasswordGenerator.generateFromPrefs(requireContext());
        tvGeneratedPassword.setText(pwd);
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

    /** Carga la configuración guardada en los controles de la UI. */
    private void loadSavedConfig() {
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PasswordGenerator.PREFS_NAME, android.content.Context.MODE_PRIVATE);

        sliderLength.setValue(prefs.getInt(PasswordGenerator.KEY_LENGTH, 16));
        switchUppercase.setChecked(prefs.getBoolean(PasswordGenerator.KEY_UPPERCASE, true));
        switchLowercase.setChecked(prefs.getBoolean(PasswordGenerator.KEY_LOWERCASE, true));
        switchNumbers  .setChecked(prefs.getBoolean(PasswordGenerator.KEY_NUMBERS,   true));
        switchSymbols  .setChecked(prefs.getBoolean(PasswordGenerator.KEY_SYMBOLS,   false));
    }
}