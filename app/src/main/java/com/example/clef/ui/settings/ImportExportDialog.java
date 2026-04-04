package com.example.clef.ui.settings;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.utils.SessionManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImportExportDialog extends BottomSheetDialogFragment {

    private MaterialButton btnExport;
    private MaterialButton btnRemoveFromCloud;
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
        btnRemoveFromCloud = view.findViewById(R.id.btnRemoveFromCloud);
        btnImport = view.findViewById(R.id.btnImport);

        btnExport.setOnClickListener(v -> onExport());
        btnRemoveFromCloud.setOnClickListener(v -> onRemoveFromCloud());
        btnImport.setOnClickListener(v -> confirmImport());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    // ── Exportar ───────────────────────────────────────────────────────────────

    private void onExport() {
        SessionManager session = SessionManager.getInstance();
        Vault vault = session.getVault();
        byte[] dek = session.getDek();

        if (vault == null || dek == null) {
            Toast.makeText(requireContext(),
                    getString(R.string.add_item_error_session), Toast.LENGTH_SHORT).show();
            return;
        }

        List<Credential> unsynced = new ArrayList<>();
        for (Credential c : vault.getCredentials()) {
            if (!c.isSynced()) unsynced.add(c);
        }

        if (unsynced.isEmpty()) {
            Toast.makeText(requireContext(),
                    getString(R.string.export_all_synced), Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_upload_select, null);
        RecyclerView rv = dialogView.findViewById(R.id.rvCredentials);
        UploadSelectAdapter adapter = new UploadSelectAdapter(requireContext(), unsynced);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnUpload).setOnClickListener(v -> {
            List<Credential> seleccionadas = adapter.getSelected();
            if (!seleccionadas.isEmpty()) {
                dialog.dismiss();
                uploadSelected(seleccionadas, vault, dek);
            }
        });

        dialog.show();
    }

    private void uploadSelected(List<Credential> seleccionadas, Vault vault, byte[] dek) {
        setFormEnabled(false);
        for (Credential c : seleccionadas) c.setSynced(true);

        executor.execute(() -> {
            try {
                // Guardar vault completo en local
                String encryptedFull = new KeyManager().cifrarVault(vault, dek);
                VaultRepository repo = new VaultRepository(requireContext());
                repo.saveLocalVaultOnly(encryptedFull);

                // Subir a Firebase solo los synced=true
                SessionManager session = SessionManager.getInstance();
                long expectedVersion = session.getCloudVaultVersion();
                repo.uploadSyncedOnly(vault, dek, expectedVersion, new VaultRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        session.setCloudVaultVersion(expectedVersion + 1);
                        session.updateVault(vault);
                        mainHandler.post(() -> {
                            setFormEnabled(true);
                            Toast.makeText(requireContext(),
                                    getString(R.string.export_success), Toast.LENGTH_SHORT).show();
                            dismiss();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        for (Credential c : seleccionadas) c.setSynced(false);
                        mainHandler.post(() -> {
                            setFormEnabled(true);
                            Toast.makeText(requireContext(),
                                    getString(R.string.export_error_network), Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                for (Credential c : seleccionadas) c.setSynced(false);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setFormEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.export_error_network), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Borrar datos de la nube ────────────────────────────────────────────────

    private void onRemoveFromCloud() {
        SessionManager session = SessionManager.getInstance();
        Vault vault = session.getVault();
        byte[] dek = session.getDek();

        if (vault == null || dek == null) {
            Toast.makeText(requireContext(),
                    getString(R.string.add_item_error_session), Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_cloud_confirm_title))
                .setMessage(getString(R.string.delete_cloud_confirm_message))
                .setPositiveButton(getString(R.string.delete_cloud_confirm_action), (d, w) ->
                        deleteAllFromCloud(vault, dek))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void deleteAllFromCloud(Vault vault, byte[] dek) {
        setFormEnabled(false);

        executor.execute(() -> {
            try {
                Vault emptyVault = new Vault();
                String encryptedEmpty = new KeyManager().cifrarVault(emptyVault, dek);
                VaultRepository repo = new VaultRepository(requireContext());
                repo.uploadSpecificVaultToFirebase(encryptedEmpty, new VaultRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        for (Credential c : vault.getCredentials()) c.setSynced(false);
                        try {
                            String encryptedLocal = new KeyManager().cifrarVault(vault, dek);
                            repo.saveLocalVaultOnly(encryptedLocal);
                        } catch (Exception ignored) {}
                        SessionManager.getInstance().updateVault(vault);
                        mainHandler.post(() -> {
                            setFormEnabled(true);
                            Toast.makeText(requireContext(),
                                    getString(R.string.delete_cloud_success), Toast.LENGTH_SHORT).show();
                            dismiss();
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        mainHandler.post(() -> {
                            setFormEnabled(true);
                            Toast.makeText(requireContext(),
                                    getString(R.string.delete_cloud_error), Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    setFormEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.delete_cloud_error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ── Importar ───────────────────────────────────────────────────────────────

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
            // Marcar todas las credenciales descargadas como synced=true (vienen de Firebase)
            for (Credential c : vault.getCredentials()) {
                c.setSynced(true);
            }
            // Guardar en local con los flags correctos
            String encryptedFull = new KeyManager().cifrarVault(vault, dek);
            SessionManager.getInstance().unlock(dek, vault);
            mainHandler.post(() -> {
                if (!isAdded()) return;
                new VaultRepository(requireContext()).saveLocalVaultOnly(encryptedFull);
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
        btnRemoveFromCloud.setEnabled(enabled);
        btnImport.setEnabled(enabled);
    }
}
