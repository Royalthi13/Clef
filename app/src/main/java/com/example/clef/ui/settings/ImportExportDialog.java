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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BottomSheet para exportar e importar la bóveda desde Firebase.
 *
 * EXPORTAR:
 *   Lee el vault cifrado del disco + claves del caché local (salt, cajaA, cajaB)
 *   y los sube todos a Firebase.
 *
 * IMPORTAR:
 *   Descarga el vault cifrado de Firebase y lo descifra con la DEK activa en sesión.
 *   El usuario ya está desbloqueado para llegar aquí, por lo que no hace falta PUK.
 *   (El PUK se pide en el flujo de dispositivo nuevo, no aquí.)
 */
public class ImportExportDialog extends BottomSheetDialogFragment {

    private MaterialButton btnExport;
    private MaterialButton btnImport;

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    public static ImportExportDialog newInstance() {
        return new ImportExportDialog();
    }

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


        btnExport.setOnClickListener(v -> onExport());

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
                    setFormEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.export_success), Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
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
                        setFormEnabled(true);
                        Toast.makeText(requireContext(),
                                getString(R.string.import_error_no_cloud_data), Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                executor.execute(() -> decryptAndUnlock(userData, dek));
            }

            @Override
            public void onError(Exception e) {
                mainHandler.post(() -> {
                    setFormEnabled(true);
                    Toast.makeText(requireContext(),
                            getString(R.string.import_error_network), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Descifra el vault descargado con la DEK activa en sesión.
     * Se ejecuta en hilo de fondo.
     */
    private void decryptAndUnlock(FirebaseManager.UserData userData, byte[] dek) {
        try {
            KeyManager km = new KeyManager();
            Vault vault = km.descifrarVault(userData.vault, dek);
            SessionManager.getInstance().unlock(dek, vault);

            mainHandler.post(() -> {
                Toast.makeText(requireContext(),
                        getString(R.string.import_success), Toast.LENGTH_SHORT).show();
                dismiss();
            });

        } catch (Exception e) {
            mainHandler.post(() -> {
                setFormEnabled(true);
                Toast.makeText(requireContext(),
                        getString(R.string.import_error_network), Toast.LENGTH_LONG).show();
            });
        }
    }


    // ── Helpers ────────────────────────────────────────────────────────────────

    private void setFormEnabled(boolean enabled) {
        btnExport.setEnabled(enabled);
        btnImport.setEnabled(enabled);
    }
}
