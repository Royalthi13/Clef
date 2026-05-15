package com.example.clef.ui.settings;

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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.signature.ObjectKey;
import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.example.clef.ui.auth.LoginActivity;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.ExpiryHelper;
import com.example.clef.utils.SecurePrefs;
import com.example.clef.utils.SessionManager;
import com.example.clef.utils.ThemeManager;
import com.example.clef.workers.PasswordExpiryWorker;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;

// FIX: Eliminado import no usado java.util.Arrays

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

        view.findViewById(R.id.rowStats).setOnClickListener(v ->
                StatsDialog.newInstance().show(getChildFragmentManager(), "stats"));

        ivAvatar    = view.findViewById(R.id.ivAvatar);
        tvUserName  = view.findViewById(R.id.tvUserName);
        tvUserEmail = view.findViewById(R.id.tvUserEmail);

        view.findViewById(R.id.cardProfile).setOnClickListener(v -> openProfileEditor());

        setupBiometricSwitch(view);
        setupThemeToggle(view);
        setupLanguage(view);
        setupAutoLock(view);
        setupSyncSwitch(view);
        setupImportExport(view);
        setupNotifications(view);
        setupSignOut(view);
        setupSettingsTips(view);
    }

    private void setupSettingsTips(View view) {
        FirebaseUser tipUser = FirebaseAuth.getInstance().getCurrentUser();
        String tipUid = tipUser != null ? tipUser.getUid() : "anon";
        SharedPreferences tipPrefs = SecurePrefs.get(requireContext(), "generator_prefs_" + tipUid);

        setupSingleTip(view, tipPrefs,
                R.id.cardSettingsSecurityTip, R.id.btnSettingsSecurityTipDismiss,
                "tip_dismissed_settings_security");
        setupSingleTip(view, tipPrefs,
                R.id.cardSettingsPreferencesTip, R.id.btnSettingsPreferencesTipDismiss,
                "tip_dismissed_settings_preferences");
        setupSingleTip(view, tipPrefs,
                R.id.cardSettingsNotificationsTip, R.id.btnSettingsNotificationsTipDismiss,
                "tip_dismissed_settings_notifications");

    }

    private void setupSingleTip(View view, SharedPreferences prefs,
                                int cardId, int btnId, String prefKey) {
        View card = view.findViewById(cardId);
        if (card == null) return;
        if (prefs.getBoolean(prefKey, false)) {
            card.setVisibility(View.GONE);
        } else {
            View btn = view.findViewById(btnId);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    prefs.edit().putBoolean(prefKey, true).apply();
                    card.animate().alpha(0f).setDuration(250)
                            .withEndAction(() -> card.setVisibility(View.GONE)).start();
                });
            }
        }
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

        SharedPreferences prefs = SecurePrefs.get(requireContext(), ProfileEditDialog.PREFS_NAME);

        String uid       = user.getUid();
        String localPath = prefs.getString(ProfileEditDialog.photoPathKey(uid), null);
        long   signature = prefs.getLong(ProfileEditDialog.photoSigKey(uid), 0L);

        // Migración: clave antigua sin UID
        if (localPath == null) {
            localPath = prefs.getString("local_photo_path", null);
            signature = prefs.getLong("photo_signature", 0L);
            if (localPath != null) {
                prefs.edit()
                        .putString(ProfileEditDialog.photoPathKey(uid), localPath)
                        .putLong(ProfileEditDialog.photoSigKey(uid), signature)
                        .remove("local_photo_path")
                        .remove("photo_signature")
                        .apply();
            }
        }

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
                            getString(R.string.add_item_error_session),
                            Toast.LENGTH_SHORT).show();
                    switchBiometrics.setChecked(false);
                    return;
                }
                BiometricHelper.enable(requireActivity(), dek, new BiometricHelper.EnableCallback() {
                    @Override public void onSuccess() {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(),
                                getString(R.string.settings_biometrics_enabled),
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        switchBiometrics.setChecked(false);
                    }
                    @Override public void onCancelled() {
                        if (!isAdded()) return;
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

    // ── Tema ──────────────────────────────────────────────────────────────────

    private void setupThemeToggle(View view) {
        MaterialButtonToggleGroup toggleTheme = view.findViewById(R.id.toggleTheme);
        ImageView ivThemeIcon = view.findViewById(R.id.ivThemeIcon);
        int savedMode = ThemeManager.load(requireContext());
        toggleTheme.clearChecked();
        if (savedMode == ThemeManager.MODE_LIGHT) toggleTheme.check(R.id.btnThemeLight);
        else if (savedMode == ThemeManager.MODE_DARK) toggleTheme.check(R.id.btnThemeDark);
        updateThemeIcon(ivThemeIcon, savedMode);

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
            updateThemeIcon(ivThemeIcon, newMode);
        });
    }

    /** Muestra luna (tema oscuro) o sol (tema claro/sistema) en el icono de tema. */
    private void updateThemeIcon(ImageView iv, int mode) {
        if (iv == null) return;
        iv.setImageResource(mode == ThemeManager.MODE_DARK
                ? R.drawable.ic_dark_mode_24
                : R.drawable.ic_light_mode_24);
    }

    // ── Idioma ────────────────────────────────────────────────────────────────

    private void setupLanguage(View view) {
        TextView tvLanguageValue = view.findViewById(R.id.tvLanguageValue);
        if (tvLanguageValue == null) return;
        tvLanguageValue.setText(currentLanguageLabel());

        view.findViewById(R.id.rowLanguage).setOnClickListener(v -> {
            String[] labels = {
                    getString(R.string.lang_es),
                    getString(R.string.lang_en)
            };
            String[] tags = { "es", "en" };

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.settings_language))
                    .setItems(labels, (d, which) -> {
                        AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(tags[which]));
                        tvLanguageValue.setText(labels[which]);
                    })
                    .show();
        });
    }

    private String currentLanguageLabel() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        String tag = locales.isEmpty()
                ? androidx.core.os.ConfigurationCompat
                        .getLocales(android.content.res.Resources.getSystem().getConfiguration())
                        .get(0).getLanguage()
                : locales.get(0).getLanguage();
        if ("es".equals(tag)) return getString(R.string.lang_es);
        return getString(R.string.lang_en);
    }

    // ── Auto-lock ─────────────────────────────────────────────────────────────

    private void setupAutoLock(View view) {
        TextView tvAutoLockValue = view.findViewById(R.id.tvAutoLockValue);
        SharedPreferences prefs = SecurePrefs.get(requireContext(), "settings");
        tvAutoLockValue.setText(msToLabel(prefs.getLong("auto_lock_ms", 300_000)));

        view.findViewById(R.id.btnAutoLockInfo).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.settings_auto_lock))
                        .setMessage("El bloqueo automático cierra la sesión tras el tiempo de inactividad elegido. Necesitarás tu contraseña maestra o biometría para volver a acceder.")
                        .setPositiveButton(getString(R.string.btn_understood), null)
                        .show());
        view.findViewById(R.id.rowAutoLock).setOnClickListener(v -> {
            String[] opciones = {
                    getString(R.string.auto_lock_1min),
                    getString(R.string.auto_lock_5min),
                    getString(R.string.auto_lock_15min),
                    getString(R.string.auto_lock_30min),
                    getString(R.string.auto_lock_never)
            };
            long[] valores = {60_000, 300_000, 900_000, 1_800_000, Long.MAX_VALUE};
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.auto_lock_dialog_title))
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
        SharedPreferences prefs = SecurePrefs.get(requireContext(), "settings");
        switchSync.setChecked(prefs.getBoolean("sync_enabled", false));
        switchSync.setOnCheckedChangeListener((btn, isChecked) ->
                prefs.edit().putBoolean("sync_enabled", isChecked).apply());

        view.findViewById(R.id.btnSyncInfo).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.settings_sync))
                        .setMessage(getString(R.string.sync_info_message))
                        .setPositiveButton(getString(R.string.btn_understood), null)
                        .show());
    }

    // ── Importar / Exportar ───────────────────────────────────────────────────

    private void setupImportExport(View view) {
        view.findViewById(R.id.rowImportExport).setOnClickListener(v ->
                ImportExportDialog.newInstance()
                        .show(getChildFragmentManager(), "import_export"));
    }

    // ── Notificaciones ────────────────────────────────────────────────────────

    private void setupNotifications(View view) {
        SwitchMaterial switchNotifications = view.findViewById(R.id.switchNotifications);
        TextView tvPeriodValue = view.findViewById(R.id.tvExpiryPeriodValue);
        SharedPreferences prefs = SecurePrefs.get(requireContext(), ExpiryHelper.PREFS_NAME);

        if (!prefs.contains(ExpiryHelper.PREF_PERIOD)) {
            prefs.edit().putLong(ExpiryHelper.PREF_PERIOD, ExpiryHelper.PERIOD_ONE_YEAR).apply();
        }

        switchNotifications.setChecked(prefs.getBoolean(ExpiryHelper.PREF_NOTIFICATIONS, false));
        tvPeriodValue.setText(periodLabel(prefs.getLong(ExpiryHelper.PREF_PERIOD, ExpiryHelper.PERIOD_ONE_YEAR)));

        switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean(ExpiryHelper.PREF_NOTIFICATIONS, isChecked).apply();
            if (isChecked) {
                PasswordExpiryWorker.schedule(requireContext());
            } else {
                PasswordExpiryWorker.cancel(requireContext());
            }
        });

        view.findViewById(R.id.rowExpiryPeriod).setOnClickListener(v -> {
            String[] options = {
                    getString(R.string.expiry_period_test),
                    getString(R.string.expiry_period_3months),
                    getString(R.string.expiry_period_6months),
                    getString(R.string.settings_expiry_period_default)
            };
            long[] values = {
                    ExpiryHelper.PERIOD_TEST,
                    ExpiryHelper.PERIOD_THREE_MONTHS,
                    ExpiryHelper.PERIOD_SIX_MONTHS,
                    ExpiryHelper.PERIOD_ONE_YEAR
            };
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.settings_expiry_period))
                    .setItems(options, (d, which) -> {
                        prefs.edit().putLong(ExpiryHelper.PREF_PERIOD, values[which]).apply();
                        tvPeriodValue.setText(options[which]);
                    })
                    .show();
        });
    }

    private String periodLabel(long ms) {
        if (ms == ExpiryHelper.PERIOD_TEST)          return getString(R.string.expiry_period_test);
        if (ms == ExpiryHelper.PERIOD_THREE_MONTHS)  return getString(R.string.expiry_period_3months);
        if (ms == ExpiryHelper.PERIOD_SIX_MONTHS)    return getString(R.string.expiry_period_6months);
        return getString(R.string.settings_expiry_period_default);
    }

    // ── Cerrar sesión ─────────────────────────────────────────────────────────

    private void setupSignOut(View view) {
        view.findViewById(R.id.btnSignOut).setOnClickListener(v ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.settings_sign_out))
                        .setMessage(getString(R.string.sign_out_message))
                        .setPositiveButton(getString(R.string.settings_sign_out), (d, w) -> performSignOut())
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show());

        view.findViewById(R.id.rowHelp).setOnClickListener(v ->
                HelpSupportSheet.show(requireContext()));
    }

    private void performSignOut() {
        BiometricHelper.disableAll(requireContext());
        SessionManager.getInstance().setOnLockListener(null);
        SessionManager.getInstance().lock();

        authManager.signOut(requireActivity(), () -> {
            if (!isAdded()) return;
            startActivity(new Intent(requireActivity(), LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String msToLabel(long ms) {
        if (ms == 60_000)    return getString(R.string.auto_lock_1min);
        if (ms == 300_000)   return getString(R.string.auto_lock_5min);
        if (ms == 900_000)   return getString(R.string.auto_lock_15min);
        if (ms == 1_800_000) return getString(R.string.auto_lock_30min);
        return getString(R.string.auto_lock_never);
    }
}