package com.example.clef.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.example.clef.ui.auth.LoginActivity;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.SessionManager;
import com.example.clef.utils.ThemeManager;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;

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

        setupBiometricSwitch(view);
        setupThemeToggle(view);

        view.findViewById(R.id.btnSignOut).setOnClickListener(v ->
                authManager.signOut(requireActivity(), () -> {
                    startActivity(new Intent(requireActivity(), LoginActivity.class));
                    requireActivity().finish();
                })
        );

        View rowHelp = view.findViewById(R.id.rowHelp);
        rowHelp.setOnClickListener(v ->
                Toast.makeText(requireContext(), "Próximamente", Toast.LENGTH_SHORT).show());

        View rowAutoLock = view.findViewById(R.id.rowAutoLock);
        rowAutoLock.setOnClickListener(v -> showAutoLockDialog());
        TextView tvAutoLockValue = view.findViewById(R.id.tvAutoLockValue);
        long savedMs = requireContext().getSharedPreferences("settings", 0)
                .getLong("auto_lock_ms", 60_000);
        tvAutoLockValue.setText(msToLabel(savedMs));

        View rowImportExport = view.findViewById(R.id.rowImportExport);
        rowImportExport.setOnClickListener(v ->
                ImportExportDialog.newInstance()
                        .show(getChildFragmentManager(), "import_export"));
    }

    private void setupBiometricSwitch(View view) {
        SwitchMaterial switchBiometrics = view.findViewById(R.id.switchBiometrics);

        // Si el dispositivo no soporta biometría fuerte, ocultamos la opción
        if (!BiometricHelper.isAvailable(requireContext())) {
            switchBiometrics.setEnabled(false);
            switchBiometrics.setChecked(false);
            return;
        }

        // Pintamos el estado real (no el hardcodeado del XML)
        switchBiometrics.setChecked(BiometricHelper.isEnabled(requireContext()));

        switchBiometrics.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                // El usuario quiere activar — necesitamos la DEK activa para cifrarla
                byte[] dek = SessionManager.getInstance().getDek();
                if (dek == null) {
                    Toast.makeText(requireContext(),
                            "La sesión expiró. Desbloquea la app de nuevo.", Toast.LENGTH_SHORT).show();
                    switchBiometrics.setChecked(false);
                    return;
                }

                BiometricHelper.enable(requireActivity(), dek, new BiometricHelper.EnableCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(requireContext(),
                                getString(R.string.settings_biometrics_enabled), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        switchBiometrics.setChecked(false);
                    }

                    @Override
                    public void onCancelled() {
                        switchBiometrics.setChecked(false);
                    }
                });
            } else {
                BiometricHelper.disable(requireContext());
                Toast.makeText(requireContext(),
                        getString(R.string.settings_biometrics_disabled), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupThemeToggle(View view) {
        MaterialButtonToggleGroup toggleTheme = view.findViewById(R.id.toggleTheme);

        // Marcar el botón que corresponde al tema guardado
        int savedMode = ThemeManager.load(requireContext());

        // Primero limpiamos cualquier selección por defecto
        toggleTheme.clearChecked();

        // Si savedMode es MODE_SYSTEM, no entra en ningún 'if' y se quedan los dos sin marcar.
        if (savedMode == ThemeManager.MODE_LIGHT) {
            toggleTheme.check(R.id.btnThemeLight);
        } else if (savedMode == ThemeManager.MODE_DARK) {
            toggleTheme.check(R.id.btnThemeDark);
        }

        toggleTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {

            int newMode;
            if (isChecked) {
                // EL USUARIO HA PULSADO UN BOTÓN
                if (checkedId == R.id.btnThemeLight) {
                    newMode = ThemeManager.MODE_LIGHT;
                } else {
                    newMode = ThemeManager.MODE_DARK;
                }
            } else {
                // EL USUARIO HA DESMARCADO UN BOTÓN
                // Preguntamos: ¿Se ha quedado el grupo entero vacío?
                if (group.getCheckedButtonId() == View.NO_ID) {
                    newMode = ThemeManager.MODE_SYSTEM; // ¡Magia! Activamos el modo sistema
                } else {
                    return; // Ignoramos esto si se desmarcó porque pulsó el otro botón
                }
            }

            if (newMode != ThemeManager.load(requireContext())) {
                ThemeManager.apply(requireContext(), newMode);
            }
        });
    }
    private void showAutoLockDialog() {
        String[] opciones = {"1 minuto", "5 minutos", "15 minutos", "30 minutos", "Nunca"};
        long[]   valores  = {60_000, 300_000, 900_000, 1_800_000, Long.MAX_VALUE};
        android.content.Context ctx = requireContext();

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("Bloqueo automático")
                .setItems(opciones, (dialog, which) -> {
                    long ms = valores[which];
                    ctx.getSharedPreferences("settings", 0)
                            .edit().putLong("auto_lock_ms", ms).apply();
                    SessionManager.getInstance().setLockTimeout(ms);
                    SessionManager.getInstance().resetTimer();
                    ((android.widget.TextView) requireActivity().findViewById(R.id.tvAutoLockValue)).setText(opciones[which]);
                    Toast.makeText(ctx, "Auto-bloqueo: " + opciones[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }
    private String msToLabel(long ms) {
        if (ms == 60_000)    return "1 minuto";
        if (ms == 300_000)   return "5 minutos";
        if (ms == 900_000)   return "15 minutos";
        if (ms == 1_800_000) return "30 minutos";
        return "Nunca";
    }

}
