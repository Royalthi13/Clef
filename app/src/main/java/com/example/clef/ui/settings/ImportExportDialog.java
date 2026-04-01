package com.example.clef.ui.settings;

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
import com.example.clef.data.model.Vault;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.utils.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImportExportDialog extends BottomSheetDialogFragment {

    private MaterialButton btnExport;
    private MaterialButton btnImport;

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    public static ImportExportDialog newInstance() { return new ImportExportDialog(); }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_import_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnExport = view.findViewById(R.id.btnExport);
        btnImport = view.findViewById(R.id.btnImport);

        btnExport.setOnClickListener(v -> onExport());
        btnImport.setOnClickListener(v -> confirmImport());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ── Exportar ───────────────────────────────────────────────────────────────

    private void onExport() {
        setFormEnabled(false);
        VaultRepository repo = new VaultRepository(requireContext());
        repo.exportToFirebase(new VaultRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setFormEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.export_success), Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setFormEnabled(true);
                    String msg = "no_local_data".equals(e.getMessage())
                            ? getString(R.string.export_error_no_data)
                            : getString(R.string.export_error_network);
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Importar ───────────────────────────────────────────────────────────────

    /**
     * FIX: Muestra un diálogo de confirmación antes de importar.
     *
     * El flujo anterior sobreescribía los datos locales sin advertencia.
     * Si el usuario tenía credenciales no sincronizadas, las perdía sin remedio.
     * Ahora se le advierte explícitamente del riesgo.
     */
    private void confirmImport() {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("¿Importar desde la nube?")
                .setMessage("Esto reemplazará las credenciales actuales del dispositivo " +
                        "con las que están guardadas en la nube. " +
                        "Los datos locales no sincronizados se perderán.")
                .setPositiveButton("Importar", (d, w) -> onImport())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void onImport() {
        byte[] dek = SessionManager.getInstance().getDek();
        if (dek == null) {
            Toast.makeText(requireContext(),
                    getString(R.string.add_item_error_session), Toast.LENGTH_SHORT).show();
            return;
        }

        setFormEnabled(false);
        VaultRepository repo = new VaultRepository(requireContext());
        repo.downloadAndCacheFromFirebase(new VaultRepository.Callback<FirebaseManager.UserData>() {
            @Override
            public void onSuccess(FirebaseManager.UserData userData) {
                if (userData == null) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        setFormEnabled(true);
                        Toast.makeText(requireContext(),
                                getString(R.string.import_error_no_cloud_data),
                                Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                executor.execute(() -> decryptAndUnlock(userData, dek));
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setFormEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.import_error_network), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void decryptAndUnlock(FirebaseManager.UserData userData, byte[] dek) {
        try {
            Vault vault = new KeyManager().descifrarVault(userData.vault, dek);
            SessionManager.getInstance().unlock(dek, vault);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        getString(R.string.import_success), Toast.LENGTH_SHORT).show();
                dismiss();
            });
        } catch (Exception e) {
            mainHandler.post(() -> {
                if (!isAdded()) return;
                setFormEnabled(true);
                Toast.makeText(requireContext(),
                        getString(R.string.import_error_network), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void setFormEnabled(boolean enabled) {
        if (!isAdded()) return;
        btnExport.setEnabled(enabled);
        btnImport.setEnabled(enabled);
    }
}