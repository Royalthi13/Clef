package com.example.clef.ui.recovery;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.ui.auth.UnlockActivity;
import com.example.clef.utils.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla de recuperación de acceso usando el código PUK.
 *
 * Flujo:
 *   1. El usuario introduce su código PUK (formato XXXX-XXXX-...).
 *   2. El usuario elige una nueva Contraseña Maestra.
 *   3. Se descarga el bundle de Firebase (salt + cajaB + vault).
 *   4. KeyManager.recoverWithPuk() abre la Caja B con el PUK → DEK.
 *   5. Se re-envuelve la DEK con la nueva contraseña → nueva Caja A.
 *   6. Se sube la nueva Caja A a Firebase.
 *   7. Se guarda la sesión en SessionManager y se navega a MainActivity.
 */
public class RecoverVaultActivity extends AppCompatActivity {

    private TextInputLayout   tilPuk;
    private TextInputLayout   tilNewPassword;
    private TextInputLayout   tilConfirmPassword;
    private TextInputEditText etPuk;
    private TextInputEditText etNewPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton    btnRecover;
    private View              loadingOverlay;

    private final ExecutorService cryptoExecutor = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler    = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recover_vault);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        tilPuk             = findViewById(R.id.tilPuk);
        tilNewPassword     = findViewById(R.id.tilNewPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etPuk              = findViewById(R.id.etPuk);
        etNewPassword      = findViewById(R.id.etNewPassword);
        etConfirmPassword  = findViewById(R.id.etConfirmPassword);
        btnRecover         = findViewById(R.id.btnRecover);
        loadingOverlay     = findViewById(R.id.loadingOverlay);

        btnRecover.setOnClickListener(v -> onRecoverClicked());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cryptoExecutor.shutdownNow();
    }

    private void onRecoverClicked() {
        tilPuk            .setError(null);
        tilNewPassword    .setError(null);
        tilConfirmPassword.setError(null);

        String puk        = text(etPuk).replace(" ", ""); // tolerar espacios al teclear
        String newPwd     = text(etNewPassword);
        String confirmPwd = text(etConfirmPassword);

        if (puk.isEmpty()) {
            tilPuk.setError("Introduce tu código PUK");
            return;
        }
        if (newPwd.length() < 8) {
            tilNewPassword.setError(getString(R.string.master_error_min_len));
            return;
        }
        if (!newPwd.equals(confirmPwd)) {
            tilConfirmPassword.setError(getString(R.string.master_error_mismatch));
            return;
        }

        setLoading(true);

        char[] pukChars = puk.toCharArray();
        char[] newPasswordChars = newPwd.toCharArray();

        // 1. Descargar datos de Firebase
        VaultRepository repo = new VaultRepository(this);
        repo.loadUserData(new VaultRepository.Callback<FirebaseManager.UserData>() {
            @Override
            public void onSuccess(FirebaseManager.UserData userData) {
                if (userData == null || userData.cajaB == null) {
                    setLoading(false);
                    Toast.makeText(RecoverVaultActivity.this,
                            "No se encontraron datos de recuperación.", Toast.LENGTH_LONG).show();
                    return;
                }
                // 2. Ejecutar la crypto en hilo de fondo
                cryptoExecutor.execute(() -> recoverInBackground(userData, pukChars, newPasswordChars, repo));
            }

            @Override
            public void onError(Exception e) {
                // Si es offline, intentar con caché local
                FirebaseManager.UserData cached = repo.loadOfflineUserData();
                if (cached != null && cached.cajaB != null) {
                    cryptoExecutor.execute(() -> recoverInBackground(cached, pukChars, newPasswordChars, repo));
                } else {
                    setLoading(false);
                    Toast.makeText(RecoverVaultActivity.this,
                            "Sin conexión y sin datos locales. Conéctate a internet.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Ejecuta el proceso criptográfico de recuperación en un hilo de fondo.
     * PBKDF2 x2 → ~800ms.
     */
    private void recoverInBackground(FirebaseManager.UserData userData,
                                     char[] pukChars,
                                     char[] newPasswordChars,
                                     VaultRepository repo) {
        try {
            KeyManager km = new KeyManager();
            KeyManager.RecoveryResult result = km.recoverWithPuk(
                    pukChars,
                    newPasswordChars,
                    userData.salt,
                    userData.cajaB,
                    userData.vault
            );

            // 3. Subir la nueva Caja A a Firebase
            repo.updateCajaA(result.nuevaCajaABase64, new VaultRepository.Callback<Void>() {
                @Override
                public void onSuccess(Void r) {
                    // 4. Guardar sesión y navegar
                    SessionManager.getInstance().unlock(result.dek, result.vault);
                    mainHandler.post(() -> {
                        Toast.makeText(RecoverVaultActivity.this,
                                "Contraseña restablecida correctamente", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(RecoverVaultActivity.this,
                                com.example.clef.ui.dashboard.MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
                }

                @Override
                public void onError(Exception e) {
                    mainHandler.post(() -> {
                        setLoading(false);
                        Toast.makeText(RecoverVaultActivity.this,
                                "Error al guardar la nueva contraseña. Inténtalo de nuevo.",
                                Toast.LENGTH_LONG).show();
                    });
                }
            });

        } catch (Exception e) {
            // AEADBadTagException → PUK incorrecto
            mainHandler.post(() -> {
                setLoading(false);
                tilPuk.setError("Código PUK incorrecto");
            });
        }
    }

    private String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        if (loadingOverlay != null)
            loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRecover.setEnabled(!loading);
        etPuk            .setEnabled(!loading);
        etNewPassword    .setEnabled(!loading);
        etConfirmPassword.setEnabled(!loading);
    }
}