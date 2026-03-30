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
import com.example.clef.utils.PasswordGenerator;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

/**
 * Fragmento de CONFIGURACIÓN del generador de contraseñas.
 *
 * Este fragmento NO genera contraseñas — solo guarda la configuración
 * (longitud y tipos de caracteres) en SharedPreferences.
 * La generación real ocurre en AddItemDialog al pulsar el icono de dado.
 */
public class GeneratorFragment extends Fragment {

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

        tvLengthValue   = view.findViewById(R.id.tvLengthValue);
        sliderLength    = view.findViewById(R.id.sliderLength);
        switchUppercase = view.findViewById(R.id.switchUppercase);
        switchLowercase = view.findViewById(R.id.switchLowercase);
        switchNumbers   = view.findViewById(R.id.switchNumbers);
        switchSymbols   = view.findViewById(R.id.switchSymbols);

        // Ocultar la sección de preview — este fragment es SOLO configuración
        View btnCopy    = view.findViewById(R.id.btnCopy);
        View btnGenerate= view.findViewById(R.id.btnGenerate);
        if (btnCopy != null)     btnCopy.setVisibility(View.GONE);
        if (btnGenerate != null) btnGenerate.setVisibility(View.GONE);

        TextView tvGenerated = view.findViewById(R.id.tvGeneratedPassword);
        if (tvGenerated != null) {
            tvGenerated.setText(
                    "Configura aquí la fortaleza de tus contraseñas.\n" +
                            "Se generarán automáticamente al crear una cuenta."
            );
            tvGenerated.setTextSize(14f);
            tvGenerated.setTextColor(
                    requireContext().getResources().getColor(
                            android.R.color.darker_gray, requireContext().getTheme()
                    )
            );
        }

        // También ocultar la card superior completa (la del label "TU NUEVA CONTRASEÑA")
        // para que quede más limpio como pantalla de configuración
        // Cargamos config y configuramos listeners
        loadSavedConfig();
        updateLengthLabel((int) sliderLength.getValue());

        sliderLength.addOnChangeListener((slider, value, fromUser) -> {
            updateLengthLabel((int) value);
            saveConfig();
        });

        switchUppercase.setOnCheckedChangeListener((btn, checked) -> saveConfig());
        switchLowercase.setOnCheckedChangeListener((btn, checked) -> saveConfig());
        switchNumbers  .setOnCheckedChangeListener((btn, checked) -> saveConfig());
        switchSymbols  .setOnCheckedChangeListener((btn, checked) -> saveConfig());
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
                .getSharedPreferences(PasswordGenerator.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        sliderLength   .setValue(prefs.getInt    (PasswordGenerator.KEY_LENGTH,    16));
        switchUppercase.setChecked(prefs.getBoolean(PasswordGenerator.KEY_UPPERCASE, true));
        switchLowercase.setChecked(prefs.getBoolean(PasswordGenerator.KEY_LOWERCASE, true));
        switchNumbers  .setChecked(prefs.getBoolean(PasswordGenerator.KEY_NUMBERS,   true));
        switchSymbols  .setChecked(prefs.getBoolean(PasswordGenerator.KEY_SYMBOLS,   false));
    }
}