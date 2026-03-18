package com.example.clef.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.clef.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class GeneratorFragment extends Fragment {

    //  Declaramos las variables para controlar el diseño
    private TextView tvGeneratedPassword;
    private TextView tvLengthValue;
    private Slider sliderLength;
    private MaterialSwitch switchUppercase, switchLowercase, switchNumbers, switchSymbols;
    private MaterialButton btnCopy, btnGenerate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // De momento, inflamos un layout vacío o el mismo de settings para no dar error
        // Luego crearemos su propio diseño visual (fragment_generator.xml)
        return inflater.inflate(R.layout.fragment_generator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Enlazamos nuestras variables Java con los IDs que pusimos en el XML
        tvGeneratedPassword = view.findViewById(R.id.tvGeneratedPassword);
        tvLengthValue = view.findViewById(R.id.tvLengthValue);
        sliderLength = view.findViewById(R.id.sliderLength);

        switchUppercase = view.findViewById(R.id.switchUppercase);
        switchLowercase = view.findViewById(R.id.switchLowercase);
        switchNumbers = view.findViewById(R.id.switchNumbers);
        switchSymbols = view.findViewById(R.id.switchSymbols);

        btnCopy = view.findViewById(R.id.btnCopy);
        btnGenerate = view.findViewById(R.id.btnGenerate);

        // Le damos valor inicial al slider
        int initialLength = (int) sliderLength.getValue();
        tvLengthValue.setText(getString(R.string.generador_tvlength, initialLength));

        //  Le damos vida al deslizador (Slider)
        sliderLength.addOnChangeListener((slider, value, fromUser) -> {
            // El slider devuelve un float (ej. 16.0), lo pasamos a entero (16) y lo mostramos
            int length = (int) value;
            tvLengthValue.setText(getString(R.string.generador_tvlength, length));
        });

        //  Dejamos el botón "Generar" escuchando para cuando lo pulsemos
        btnGenerate.setOnClickListener(v -> {
            // Aquí meteremos la lógica para crear la contraseña en el siguiente paso
        });

        //  Dejamos el botón "Copiar" escuchando
        btnCopy.setOnClickListener(v -> {
            // Aquí meteremos la lógica del portapapeles
        });
    }
}
