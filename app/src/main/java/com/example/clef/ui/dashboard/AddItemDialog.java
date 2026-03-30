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
import com.example.clef.utils.SessionManager;
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

    private com.google.android.material.chip.ChipGroup chipGroupCategory;
    private TextInputLayout   tilTitle;
    private TextInputLayout   tilUsername;
    private TextInputLayout   tilPassword;
    private TextInputEditText etTitle;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etUrl;         // ← NUEVO
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
        etUrl       = view.findViewById(R.id.etUrl);         // ← NUEVO
        etDescription = view.findViewById(R.id.etDescription);
        btnSave     = view.findViewById(R.id.btnSave);
        chipGroupCategory = view.findViewById(R.id.chipGroupCategory);

        // Chips de categoría
        for (Credential.Category cat : Credential.Category.values()) {
            com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(requireContext());
            chip.setText(getString(cat.getLabelRes()));
            chip.setCheckable(true);
            chip.setTag(cat);
            chipGroupCategory.addView(chip);
        }

        // Icono de dado → genera contraseña con config del Generador
        tilPassword.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        tilPassword.setEndIconDrawable(R.drawable.ic_generator);
        tilPassword.setEndIconContentDescription("Generar contraseña");
        tilPassword.setEndIconOnClickListener(v -> {
            String generated = PasswordGenerator.generateFromPrefs(requireContext());
            etPassword.setText(generated);
            etPassword.setSelection(generated.length());
        });

        btnSave.setOnClickListener(v -> onSave());
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
        String url      = text(etUrl);           // ← NUEVO
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

        int checkedId = chipGroupCategory.getCheckedChipId();
        Credential.Category category = checkedId != View.NO_ID
                ? (Credential.Category) chipGroupCategory.findViewById(checkedId).getTag()
                : Credential.Category.OTHER;

        boolean syncEnabled = requireContext()
                .getSharedPreferences("settings", 0)
                .getBoolean("sync_enabled", false);

        // ← url se pasa ahora al constructor en lugar de ""
        Credential credential = new Credential(title, username, password, url, notes, category);
        vault.addCredential(credential);

        executor.execute(() -> {
            try {
                KeyManager km = new KeyManager();
                String encryptedVault = km.cifrarVault(vault, dek);

                VaultRepository repo = new VaultRepository(requireContext());
                repo.saveVault(encryptedVault, new VaultRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        credential.setSynced(syncEnabled);
                        session.updateVault(vault);
                        mainHandler.post(() -> {
                            if (listener != null) listener.onCredentialSaved();
                            dismiss();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        vault.removeCredential(vault.getCredentials().size() - 1);
                        mainHandler.post(() -> {
                            setFormEnabled(true);
                            Toast.makeText(requireContext(),
                                    getString(R.string.add_item_error_save), Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                vault.removeCredential(vault.getCredentials().size() - 1);
                mainHandler.post(() -> {
                    setFormEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.add_item_error_save), Toast.LENGTH_LONG).show();
                });
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