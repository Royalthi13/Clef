package com.example.clef.ui.dashboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.clef.R;
import com.example.clef.utils.PasswordGenerator;
import com.example.clef.utils.SecurePrefs;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class PasswordGeneratorSheet {

    public interface OnPasswordGeneratedListener {
        void onPasswordGenerated(String password);
    }

    public static void show(Context context, OnPasswordGeneratedListener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_generator_sheet, null);
        dialog.setContentView(view);

        if (context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            dialog.setOnShowListener(d -> {
                FrameLayout bs = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bs != null) {
                    BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bs);
                    behavior.setSkipCollapsed(true);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            });
        }

        TextView       tvLength   = view.findViewById(R.id.tvLengthValue);
        Slider         slider     = view.findViewById(R.id.sliderLength);
        MaterialSwitch swUpper    = view.findViewById(R.id.switchUppercase);
        MaterialSwitch swLower    = view.findViewById(R.id.switchLowercase);
        MaterialSwitch swNumbers  = view.findViewById(R.id.switchNumbers);
        MaterialSwitch swSymbols  = view.findViewById(R.id.switchSymbols);
        MaterialButton btnGenerate = view.findViewById(R.id.btnGenerate);

        SharedPreferences prefs = SecurePrefs.get(context, PasswordGenerator.PREFS_NAME);
        slider   .setValue(Math.max(8, Math.min(64, prefs.getInt(PasswordGenerator.KEY_LENGTH, 16))));
        swUpper  .setChecked(prefs.getBoolean(PasswordGenerator.KEY_UPPERCASE, true));
        swLower  .setChecked(prefs.getBoolean(PasswordGenerator.KEY_LOWERCASE, true));
        swNumbers.setChecked(prefs.getBoolean(PasswordGenerator.KEY_NUMBERS,   true));
        swSymbols.setChecked(prefs.getBoolean(PasswordGenerator.KEY_SYMBOLS,   false));
        tvLength .setText(String.valueOf((int) slider.getValue()));

        slider.addOnChangeListener((s, value, fromUser) ->
                tvLength.setText(String.valueOf((int) value)));

        btnGenerate.setOnClickListener(v -> {
            int     length  = (int) slider.getValue();
            boolean upper   = swUpper.isChecked();
            boolean lower   = swLower.isChecked();
            boolean numbers = swNumbers.isChecked();
            boolean symbols = swSymbols.isChecked();
            PasswordGenerator.saveConfig(context, length, upper, lower, numbers, symbols);
            String password = PasswordGenerator.generate(length, upper, lower, numbers, symbols);
            dialog.dismiss();
            if (listener != null) listener.onPasswordGenerated(password);
        });

        dialog.show();
    }
}
