package com.example.clef.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.example.clef.ui.auth.LoginActivity;
import com.example.clef.utils.ThemeManager;
import com.google.android.material.button.MaterialButtonToggleGroup;

public class SettingsFragment extends Fragment {

    private AuthManager authManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authManager = new AuthManager(requireActivity(), getString(R.string.default_web_client_id));

        setupThemeToggle(view);

        view.findViewById(R.id.btnSignOut).setOnClickListener(v ->
                authManager.signOut(requireActivity(), () -> {
                    startActivity(new Intent(requireActivity(), LoginActivity.class));
                    requireActivity().finish();
                })
        );

        view.findViewById(R.id.btnChangeMaster).setOnClickListener(v -> {
            // TODO: flujo cambio de contraseña maestra
        });

        view.findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> {
            // TODO: confirmar y borrar cuenta
        });
    }

    private void setupThemeToggle(View view) {
        MaterialButtonToggleGroup toggleTheme = view.findViewById(R.id.toggleTheme);

        // Marcar el botón que corresponde al tema guardado
        int savedMode = ThemeManager.load(requireContext());
        if (savedMode == AppCompatDelegate.MODE_NIGHT_NO) {
            toggleTheme.check(R.id.btnThemeLight);
        } else if (savedMode == AppCompatDelegate.MODE_NIGHT_YES) {
            toggleTheme.check(R.id.btnThemeDark);
        } else {
            toggleTheme.check(R.id.btnThemeSystem);
        }

        toggleTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int newMode;
            if (checkedId == R.id.btnThemeLight) {
                newMode = ThemeManager.MODE_LIGHT;
            } else if (checkedId == R.id.btnThemeDark) {
                newMode = ThemeManager.MODE_DARK;
            } else {
                newMode = ThemeManager.MODE_SYSTEM;
            }
            if (newMode != ThemeManager.load(requireContext())) {
                ThemeManager.apply(requireContext(), newMode);
            }
        });
    }
}
