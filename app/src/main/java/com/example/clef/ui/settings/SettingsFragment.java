package com.example.clef.ui.settings;

import android.content.Context;
import android.content.Intent;
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
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
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

    private ImageView ivAvatar;
    private TextView  tvUserName;
    private TextView  tvUserEmail;

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

        authManager = new AuthManager(requireActivity(),
                getString(R.string.default_web_client_id));

        ivAvatar    = view.findViewById(R.id.ivAvatar);
        tvUserName  = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);

        loadUserProfile();

        // Card de perfil → abre el BottomSheet de edición
        view.findViewById(R.id.cardProfile).setOnClickListener(v -> openProfileEditor());

        setupBiometricSwitch(view);
        setupThemeToggle(view);

        view.findViewById(R.id.btnSignOut).setOnClickListener(v ->
                authManager.signOut(requireActivity(), () -> {
                    startActivity(new Intent(requireActivity(), LoginActivity.class));
                    requireActivity().finish();
                })
        );

        view.findViewById(R.id.rowHelp).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Próximamente", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.rowAutoLock).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Próximamente", Toast.LENGTH_SHORT).show());

        view.findViewById(R.id.rowImportExport).setOnClickListener(v ->
                ImportExportDialog.newInstance()
                        .show(getChildFragmentManager(), "import_export"));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refrescamos el perfil cada vez que volvemos al fragment
        loadUserProfile();
    }

    // ── Perfil ─────────────────────────────────────────────────────────────────

    /**
     * Carga los datos del perfil en el card.
     *
     * Orden de prioridad para la foto:
     *   1. Archivo local guardado en filesDir (foto elegida desde galería)
     *   2. Foto de Google (solo si el usuario se registró con Google y hay red)
     *   3. Icono genérico con tint
     *
     * IMPORTANTE: llamamos a clearColorFilter() antes de cargar cualquier
     * foto real para que el tint del layout no oscurezca la imagen.
     */
    private void loadUserProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !isAdded()) return;

        // ── Nombre ──
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isEmpty()) {
            tvUserName.setText(displayName);
        } else {
            String email = user.getEmail();
            if (email != null && email.contains("@")) {
                tvUserName.setText(email.substring(0, email.indexOf('@')));
            } else {
                tvUserName.setText(getString(R.string.profile_default_name));
            }
        }

        // ── Email ──
        String email = user.getEmail();
        tvUserEmail.setText(email != null ? email : "");

        // ── Foto ──
        File localPhoto = getLocalPhotoFile();
        if (localPhoto != null) {
            // Foto local de galería — quitamos el tint antes de cargar
            ivAvatar.clearColorFilter();
            Glide.with(requireContext())
                    .load(localPhoto)
                    .transform(new CircleCrop())
                    .placeholder(R.drawable.ic_person_24)
                    .error(R.drawable.ic_person_24)
                    .into(ivAvatar);
        } else {
            Uri googlePhoto = user.getPhotoUrl();
            if (googlePhoto != null) {
                // Foto de Google — quitamos el tint antes de cargar
                ivAvatar.clearColorFilter();
                Glide.with(requireContext())
                        .load(googlePhoto)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person_24)
                        .error(R.drawable.ic_person_24)
                        .into(ivAvatar);
            } else {
                // Sin foto — icono genérico con su tint original del layout
                ivAvatar.setImageResource(R.drawable.ic_person_24);
            }
        }
    }

    /**
     * Lee la ruta de la foto local desde SharedPreferences.
     * Devuelve null si nunca se guardó una o si el archivo ya no existe.
     */
    private File getLocalPhotoFile() {
        if (!isAdded()) return null;
        String path = requireContext()
                .getSharedPreferences(ProfileEditDialog.PREFS_PROFILE, Context.MODE_PRIVATE)
                .getString(ProfileEditDialog.KEY_PHOTO_PATH, null);
        if (path == null) return null;
        File f = new File(path);
        return f.exists() ? f : null;
    }

    /**
     * Abre el BottomSheet de edición de perfil.
     * El callback refresca el card inmediatamente sin esperar a onResume.
     */
    private void openProfileEditor() {
        ProfileEditDialog dialog = ProfileEditDialog.newInstance();
        dialog.setOnProfileUpdatedListener((newName, localPhoto) -> {
            tvUserName.setText(newName);

            if (localPhoto != null && isAdded()) {
                // Quitamos el tint antes de cargar la foto nueva
                ivAvatar.clearColorFilter();
                Glide.with(requireContext())
                        .load(localPhoto)
                        .transform(new CircleCrop())
                        .placeholder(R.drawable.ic_person_24)
                        .into(ivAvatar);
            }
        });
        dialog.show(getChildFragmentManager(), "profile_edit");
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
                    @Override
                    public void onSuccess() {
                        Toast.makeText(requireContext(),
                                getString(R.string.settings_biometrics_enabled),
                                Toast.LENGTH_SHORT).show();
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
                        getString(R.string.settings_biometrics_disabled),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Tema ───────────────────────────────────────────────────────────────────

    private void setupThemeToggle(View view) {
        MaterialButtonToggleGroup toggleTheme = view.findViewById(R.id.toggleTheme);

        int savedMode = ThemeManager.load(requireContext());
        toggleTheme.clearChecked();

        if (savedMode == ThemeManager.MODE_LIGHT) {
            toggleTheme.check(R.id.btnThemeLight);
        } else if (savedMode == ThemeManager.MODE_DARK) {
            toggleTheme.check(R.id.btnThemeDark);
        }

        toggleTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            int newMode;
            if (isChecked) {
                newMode = (checkedId == R.id.btnThemeLight)
                        ? ThemeManager.MODE_LIGHT
                        : ThemeManager.MODE_DARK;
            } else {
                if (group.getCheckedButtonId() == View.NO_ID) {
                    newMode = ThemeManager.MODE_SYSTEM;
                } else {
                    return;
                }
            }
            if (newMode != ThemeManager.load(requireContext())) {
                ThemeManager.apply(requireContext(), newMode);
            }
        });
    }
}