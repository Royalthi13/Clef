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
import com.example.clef.utils.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BottomSheet para añadir una credencial nueva a la bóveda.
 *
 * Campos:
 *   - Título (obligatorio)   → Credential.title
 *   - Correo / usuario (obligatorio) → Credential.username
 *   - Contraseña (obligatoria, con toggle ver/ocultar) → Credential.password
 *   - Descripción (opcional) → Credential.notes
 *
 * Al guardar:
 *   1. Lee el Vault en memoria desde SessionManager.
 *   2. Añade la Credential al Vault.
 *   3. Lo cifra con la DEK activa (hilo de fondo).
 *   4. Lo sube a Firebase + caché local a través de VaultRepository.
 *   5. Actualiza el Vault en SessionManager.
 *   6. Llama al listener para que VaultFragment refresque la lista.
 */
public class AddItemDialog extends BottomSheetDialogFragment {

    public interface OnCredentialSavedListener {
        void onCredentialSaved();
    }

    private OnCredentialSavedListener listener;

    private TextInputLayout   tilTitle;
    private TextInputLayout   tilUsername;
    private TextInputLayout   tilPassword;
    private TextInputEditText etTitle;
    private TextInputEditText etUsername;
    private TextInputEditText etPassword;
    private TextInputEditText etDescription;
    private MaterialButton    btnSave;

    private final ExecutorService executor   = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    public static AddItemDialog newInstance() {
        return new AddItemDialog();
    }

    public void setOnCredentialSavedListener(OnCredentialSavedListener l) {
        this.listener = l;
    }

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

        tilTitle       = view.findViewById(R.id.tilTitle);
        tilUsername    = view.findViewById(R.id.tilUsername);
        tilPassword    = view.findViewById(R.id.tilPassword);
        etTitle        = view.findViewById(R.id.etTitle);
        etUsername     = view.findViewById(R.id.etUsername);
        etPassword     = view.findViewById(R.id.etPassword);
        etDescription  = view.findViewById(R.id.etDescription);
        btnSave        = view.findViewById(R.id.btnSave);

        btnSave.setOnClickListener(v -> onSave());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void onSave() {
        // Validación
        String title    = text(etTitle);
        String username = text(etUsername);
        String password = text(etPassword);
        String notes    = text(etDescription);

        boolean valid = true;
        if (title.isEmpty()) {
            tilTitle.setError(getString(R.string.add_item_error_required));
            valid = false;
        } else {
            tilTitle.setError(null);
        }
        if (username.isEmpty()) {
            tilUsername.setError(getString(R.string.add_item_error_required));
            valid = false;
        } else {
            tilUsername.setError(null);
        }
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.add_item_error_required));
            valid = false;
        } else {
            tilPassword.setError(null);
        }
        if (!valid) return;

        // Sesión activa
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

        Credential credential = new Credential(title, username, password, "", notes);
        vault.addCredential(credential);

        executor.execute(() -> {
            try {
                KeyManager km = new KeyManager();
                String encryptedVault = km.cifrarVault(vault, dek);

                VaultRepository repo = new VaultRepository(requireContext());
                repo.saveVault(encryptedVault, new VaultRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        session.updateVault(vault);
                        mainHandler.post(() -> {
                            if (listener != null) listener.onCredentialSaved();
                            dismiss();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        // Revertimos el cambio en memoria si falla Firebase
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
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setFormEnabled(boolean enabled) {
        etTitle.setEnabled(enabled);
        etUsername.setEnabled(enabled);
        etPassword.setEnabled(enabled);
        etDescription.setEnabled(enabled);
        btnSave.setEnabled(enabled);
    }
}
