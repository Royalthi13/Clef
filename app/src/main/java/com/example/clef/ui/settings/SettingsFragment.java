package com.example.clef.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.signature.ObjectKey;
import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.ui.auth.LoginActivity;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.SessionManager;
import com.example.clef.utils.ThemeManager;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;

public class SettingsFragment extends Fragment {

    private AuthManager authManager;
    private ImageView   ivAvatar;
    private TextView    tvUserName;
    private TextView    tvUserEmail;

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

        ivAvatar    = view.findViewById(R.id.ivAvatar);
        tvUserName  = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);

        view.findViewById(R.id.cardProfile).setOnClickListener(v -> openProfileEditor());

        setupBiometricSwitch(view);
        setupThemeToggle(view);
        setupAutoLock(view);
        setupSyncSwitch(view);
        setupImportExport(view);
        setupSignOut(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserProfile();
    }

    // ── Perfil ─────────────────────────────────────────────────────────────────

    private void loadUserProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !isAdded()) return;

        String name = user.getDisplayName();
        if (name != null && !name.isEmpty()) {
            tvUserName.setText(name);
        } else {
            String email = user.getEmail();
            tvUserName.setText(email != null && email.contains("@")
                    ? email.substring(0, email.indexOf('@'))
                    : getString(R.string.profile_default_name));
        }
        tvUserEmail.setText(user.getEmail() != null ? user.getEmail() : "");

        // Claves de foto específicas del usuario actual — soluciona el bug de
        // "foto de cuenta A visible al entrar con cuenta B"
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(ProfileEditDialog.PREFS_NAME, Context.MODE_PRIVATE);
        String localPath = prefs.getString(ProfileEditDialog.photoPathKey(user.getUid()), null);
        long   signature = prefs.getLong(ProfileEditDialog.photoSigKey(user.getUid()), 0L);

        if (localPath != null) {
            File f = new File(localPath);
            if (f.exists()) {
                ivAvatar.clearColorFilter();
                Glide.with(this)
                        .load(f)
                        .signature(new ObjectKey(signature))
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person_24)
                        .into(ivAvatar);
                return;
            }
        }

        Uri googlePhoto = user.getPhotoUrl();
        if (googlePhoto != null) {
            ivAvatar.clearColorFilter();
            Glide.with(this)
                    .load(googlePhoto)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person_24)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(ivAvatar);
        } else {
            ivAvatar.setImageResource(R.drawable.ic_person_24);
        }
    }

    private void openProfileEditor() {
        ProfileEditDialog dialog = ProfileEditDialog.newInstance();
        dialog.setOnProfileUpdatedListener((newName, localPhoto) -> {
            tvUserName.setText(newName);
            loadUserProfile();
        });
        dialog.show(getChildFragmentManager(), "edit_profile");
    }

    // ── Biometría ──────────────────────────────────────────────────────────────

    private void setupBiometricSwitch(View view) {
        SwitchMaterial switchBiometrics = view.findViewById(R.id.switchBiometrics);

        if (!BiometricHelper.isAvailable(requireContext())) {
            switchBiometrics.setEnabled(false);
            switchBiometrics.setChecked(false);
            return;
        }

        switchBiometrics.setChecked(BiometricHelper.isEnabled(requireContext()));
        switchBiometrics.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                byte[] dek = SessionManager.getInstance().getDek();
                if (dek == null) {
                    Toast.makeText(requireContext(),
                            "La sesión expiró. Desbloquea la app de nuevo.",
                            Toast.LENGTH_SHORT).show();
                    switchBiometrics.setChecked(false);
                    return;
                }
                BiometricHelper.enable(requireActivity(), dek, new BiometricHelper.EnableCallback() {
                    @Override public void onSuccess() {
                        Toast.makeText(requireContext(),
                                getString(R.string.settings_biometrics_enabled),
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String message) {
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        switchBiometrics.setChecked(false);
                    }
                    @Override public void onCancelled() { switchBiometrics.setChecked(false); }
                });
            } else {
                BiometricHelper.disable(requireContext());
                Toast.makeText(requireContext(),
                        getString(R.string.settings_biometrics_disabled),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Tema ──────────────────────────────────────────────────────────────────

    private void setupThemeToggle(View view) {
        MaterialButtonToggleGroup toggleTheme = view.findViewById(R.id.toggleTheme);
        int savedMode = ThemeManager.load(requireContext());
        toggleTheme.clearChecked();
        if (savedMode == ThemeManager.MODE_LIGHT) toggleTheme.check(R.id.btnThemeLight);
        else if (savedMode == ThemeManager.MODE_DARK) toggleTheme.check(R.id.btnThemeDark);

        toggleTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            int newMode;
            if (isChecked) {
                newMode = (checkedId == R.id.btnThemeLight)
                        ? ThemeManager.MODE_LIGHT : ThemeManager.MODE_DARK;
            } else {
                if (group.getCheckedButtonId() == View.NO_ID) newMode = ThemeManager.MODE_SYSTEM;
                else return;
            }
            if (newMode != ThemeManager.load(requireContext())) {
                ThemeManager.apply(requireContext(), newMode);
            }
        });
    }

    // ── Auto-lock ─────────────────────────────────────────────────────────────

    private void setupAutoLock(View view) {
        TextView tvAutoLockValue = view.findViewById(R.id.tvAutoLockValue);
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE);
        tvAutoLockValue.setText(msToLabel(prefs.getLong("auto_lock_ms", 60_000)));

        view.findViewById(R.id.rowAutoLock).setOnClickListener(v -> {
            String[] opciones = {"1 minuto", "5 minutos", "15 minutos", "30 minutos", "Nunca"};
            long[]   valores  = {60_000, 300_000, 900_000, 1_800_000, Long.MAX_VALUE};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Bloqueo automático")
                    .setItems(opciones, (d, which) -> {
                        long ms = valores[which];
                        prefs.edit().putLong("auto_lock_ms", ms).apply();
                        SessionManager.getInstance().setLockTimeout(ms);
                        SessionManager.getInstance().resetTimer();
                        tvAutoLockValue.setText(opciones[which]);
                    })
                    .show();
        });
    }

    // ── Sincronización ────────────────────────────────────────────────────────

    private void setupSyncSwitch(View view) {
        SwitchMaterial switchSync = view.findViewById(R.id.switchSync);
        SharedPreferences prefs = requireContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE);
        switchSync.setChecked(prefs.getBoolean("sync_enabled", false));
        switchSync.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean("sync_enabled", isChecked).apply());

        view.findViewById(R.id.btnSyncInfo).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.settings_sync))
                        .setMessage(getString(R.string.sync_info_message))
                        .setPositiveButton("Entendido", null)
                        .show());
    }

    // ── Importar / Exportar ───────────────────────────────────────────────────

    private void setupImportExport(View view) {
        view.findViewById(R.id.rowImportExport).setOnClickListener(v ->
                ImportExportDialog.newInstance()
                        .show(getChildFragmentManager(), "import_export"));
    }

    // ── Cerrar sesión ─────────────────────────────────────────────────────────

    private void setupSignOut(View view) {
        view.findViewById(R.id.btnSignOut).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Cerrar sesión")
                        .setMessage("Se eliminará el vault local y los datos biométricos de este dispositivo.")
                        .setPositiveButton("Cerrar sesión", (d, w) -> performSignOut())
                        .setNegativeButton("Cancelar", null)
                        .show());

        view.findViewById(R.id.rowHelp).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Próximamente", Toast.LENGTH_SHORT).show());
    }

    /**
     * Cierre de sesión limpio y seguro.
     *
     * Orden de operaciones:
     *   1. Limpiar DEK de memoria (SessionManager.lock)
     *   2. Borrar DEK cifrada del disco (BiometricHelper.disable)
     *   3. Borrar caché de claves criptográficas (salt, cajaA, cajaB)
     *   4. Borrar vault local (vault.enc)
     *   5. Borrar prefs de foto de perfil
     *   6. Cerrar sesión en Firebase y Google
     *
     * Antes, signOut() no hacía nada de esto — la DEK cifrada y el caché de
     * claves permanecían en disco tras el logout, causando que la biometría
     * y los datos del usuario anterior fueran accesibles al siguiente usuario.
     */
    private void performSignOut() {
        // 1. Limpiar sesión en memoria
        SessionManager.getInstance().lock();

        // 2. Borrar biometría del usuario actual
        BiometricHelper.disable(requireContext());

        // 3. Borrar caché de claves criptográficas
        new VaultRepository(requireContext()).clearKeyCache();

        // 4. Borrar vault local
        new VaultRepository(requireContext()).clearLocalVault();

        // 5. Borrar foto de perfil local (las prefs contienen ruta + signatura)
        requireContext()
                .getSharedPreferences(ProfileEditDialog.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply();

        // 6. Cerrar sesión Firebase + Google → navegar a Login
        authManager.signOut(requireActivity(), () -> {
            startActivity(new Intent(requireActivity(), LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String msToLabel(long ms) {
        if (ms == 60_000)    return "1 minuto";
        if (ms == 300_000)   return "5 minutos";
        if (ms == 900_000)   return "15 minutos";
        if (ms == 1_800_000) return "30 minutos";
        return "Nunca";
    }
}