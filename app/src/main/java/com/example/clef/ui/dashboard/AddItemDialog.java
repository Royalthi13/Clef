package com.example.clef.ui.dashboard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.utils.PasswordGenerator;
import com.example.clef.utils.SecurePrefs;
import com.example.clef.utils.SessionManager;
import android.content.res.Configuration;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class AddItemDialog extends BottomSheetDialogFragment {

    public interface OnCredentialSavedListener {
        void onCredentialSaved();
    }

    private OnCredentialSavedListener listener;
    private com.google.android.material.textfield.MaterialAutoCompleteTextView acCategory;
    private Credential.Category selectedCategory = null;
    private boolean categoryManuallyChanged = false;
    private TextInputLayout   tilTitle;
    private TextInputLayout   tilUsername;
    private TextInputLayout   tilPassword;
    private TextInputEditText etTitle;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etUrl;
    private TextInputEditText etDescription;
    private MaterialButton    btnSave;

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    public static AddItemDialog newInstance() { return new AddItemDialog(); }

    public void setOnCredentialSavedListener(OnCredentialSavedListener l) { this.listener = l; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_add_item, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tilTitle    = view.findViewById(R.id.tilTitle);
        tilUsername = view.findViewById(R.id.tilUsername);
        tilPassword = view.findViewById(R.id.tilPassword);
        etTitle     = view.findViewById(R.id.etTitle);
        etUsername  = view.findViewById(R.id.etUsername);
        etPassword  = view.findViewById(R.id.etPassword);
        etUrl       = view.findViewById(R.id.etUrl);
        etDescription = view.findViewById(R.id.etDescription);
        btnSave     = view.findViewById(R.id.btnSave);
        acCategory  = view.findViewById(R.id.acCategory);

        Credential.Category[] cats = Credential.Category.values();
        String[] catLabels = new String[cats.length];
        for (int i = 0; i < cats.length; i++) catLabels[i] = getString(cats[i].getLabelRes());

        android.widget.ArrayAdapter<String> catAdapter = new android.widget.ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                catLabels);
        acCategory.setAdapter(catAdapter);
        acCategory.setOnItemClickListener((parent, v, pos, id) -> {
            selectedCategory = cats[pos];
            categoryManuallyChanged = true;
        });

        // Autodetección en tiempo real mientras escribe título/URL, salvo que el
        // usuario ya haya tocado el selector manualmente.
        android.text.TextWatcher autoCat = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (categoryManuallyChanged) return;
                Credential.Category detected = com.example.clef.utils.CategoryDetector
                        .detect(text(etTitle), text(etUrl));
                if (detected != null && detected != selectedCategory) {
                    selectedCategory = detected;
                    acCategory.setText(getString(detected.getLabelRes()), false);
                }
            }
        };
        etTitle.addTextChangedListener(autoCat);
        etUrl.addTextChangedListener(autoCat);
        tilPassword.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        tilPassword.setEndIconDrawable(R.drawable.ic_generator);
        tilPassword.setEndIconContentDescription("Generar contraseña");
        tilPassword.setEndIconOnClickListener(v -> {
            String generated = PasswordGenerator.generateFromPrefs(requireContext());
            etPassword.setText(generated);
            etPassword.setSelection(generated.length());
        });

        android.widget.ImageButton btnShowPassword = view.findViewById(R.id.btnShowPassword);
        com.example.clef.utils.PasswordVisibilityToggle.attach(etPassword, btnShowPassword);

        android.widget.LinearLayout sBar1 = view.findViewById(R.id.strengthBar1);
        android.widget.LinearLayout sBar2 = view.findViewById(R.id.strengthBar2);
        android.widget.LinearLayout sBar3 = view.findViewById(R.id.strengthBar3);
        android.widget.TextView     sLabel = view.findViewById(R.id.tvStrengthLabel);

        etPassword.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int i, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                com.example.clef.utils.PasswordStrengthHelper.update(
                        requireContext(), s.toString(), sBar1, sBar2, sBar3, sLabel);
            }
        });

        btnSave.setOnClickListener(v -> onSave());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
            if (dialog != null) {
                BottomSheetBehavior<android.widget.FrameLayout> behavior = dialog.getBehavior();
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void onSave() {
        String title    = text(etTitle);
        String username = text(etUsername);
        String password = text(etPassword);
        String url      = text(etUrl);
        String notes    = text(etDescription);

        boolean valid = true;
        if (title.isEmpty()) {
            tilTitle.setError(getString(R.string.add_item_error_required));
            valid = false;
        } else tilTitle.setError(null);

        if (username.isEmpty()) {
            tilUsername.setError(getString(R.string.add_item_error_required));
            valid = false;
        } else tilUsername.setError(null);

        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.add_item_error_required));
            valid = false;
        } else tilPassword.setError(null);

        if (!valid) return;

        SessionManager session = SessionManager.getInstance();
        byte[] dek   = session.getDek();
        Vault  vault = session.getVault();

        if (dek == null || vault == null) {
            Toast.makeText(requireContext(),
                    getString(R.string.add_item_error_session), Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        setFormEnabled(false);

        Credential.Category category = selectedCategory != null
                ? selectedCategory : Credential.Category.OTHER;

        boolean syncEnabled = SecurePrefs.get(requireContext(), "settings")
                .getBoolean("sync_enabled", false);

        Credential credential = new Credential(title, username, password, url, notes, category);
        credential.setUpdatedAt(System.currentTimeMillis());
        vault.addCredential(credential);

        credential.setSynced(syncEnabled);

        executor.execute(() -> {
            try {
                KeyManager km = new KeyManager();
                String encryptedVault = km.cifrarVault(vault, dek);
                VaultRepository repo = new VaultRepository(requireContext());

                // Siempre guardar el vault completo en local
                repo.saveLocalVaultOnly(encryptedVault);

                if (syncEnabled) {
                    // Subir a Firebase solo las credenciales con synced=true
                    long expectedVersion = session.getCloudVaultVersion();
                    repo.uploadSyncedOnly(vault, dek, expectedVersion, new VaultRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            session.setCloudVaultVersion(expectedVersion + 1);
                            session.updateVault(vault);
                            mainHandler.post(() -> {
                                if (listener != null) listener.onCredentialSaved();
                                dismiss();
                            });
                        }
                        @Override
                        public void onError(Exception e) {
                            session.updateVault(vault);
                            mainHandler.post(() -> {
                                if (listener != null) listener.onCredentialSaved();
                                dismiss();
                            });
                        }
                    });
                } else {
                    session.updateVault(vault);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onCredentialSaved();
                        dismiss();
                    });
                }
            } catch (Exception e) {
                vault.getCredentials().remove(credential);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setFormEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.add_item_error_save), Toast.LENGTH_LONG).show();
                });
            } finally {
                SessionManager.zeroizeDekCopy(dek);
            }
        });
    }

    private String text(TextInputEditText et) {
        if (et == null) return "";
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setFormEnabled(boolean enabled) {
        etTitle      .setEnabled(enabled);
        etUsername   .setEnabled(enabled);
        etPassword   .setEnabled(enabled);
        if (etUrl != null) etUrl.setEnabled(enabled);
        etDescription.setEnabled(enabled);
        btnSave      .setEnabled(enabled);
    }
}
