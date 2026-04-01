package com.example.clef.ui.recovery;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.crypto.CryptoUtils;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.ui.dashboard.MainActivity;
import com.example.clef.ui.setup.ShowPukActivity;
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
 *   1. El usuario introduce su código PUK y una nueva Contraseña Maestra.
 *   2. Se descarga el bundle de Firebase (salt + cajaB + vault).
 *   3. KeyManager.recoverWithPuk() abre la Caja B con el PUK → DEK.
 *   4. Se re-envuelve la DEK con la nueva contraseña → nueva Caja A.
 *   5. FIX: Se genera un nuevo PUK y una nueva Caja B para que el PUK
 *      original quede completamente invalidado (no solo "marcado").
 *   6. Se sube la nueva Caja A + nueva Caja B + nuevo salt-puk a Firebase.
 *   7. El nuevo PUK se muestra una sola vez en ShowPukActivity.
 *   8. La sesión se guarda en SessionManager y se navega a MainActivity.
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

        String puk        = text(etPuk).replace(" ", "").replace("-", "");
        // Toleramos que el usuario escriba con o sin guiones
        String pukConGuiones = formatearPukConGuiones(text(etPuk).replace(" ", ""));
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

        // Normalizar el PUK: si el usuario lo escribió sin guiones, añadirlos
        String pukNormalizado = pukConGuiones.isEmpty() ? text(etPuk).replace(" ", "") : pukConGuiones;
        char[] pukChars = pukNormalizado.toCharArray();
        char[] newPasswordChars = newPwd.toCharArray();

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
                cryptoExecutor.execute(() ->
                        recoverInBackground(userData, pukChars, newPasswordChars, repo));
            }

            @Override
            public void onError(Exception e) {
                FirebaseManager.UserData cached = repo.loadOfflineUserData();
                if (cached != null && cached.cajaB != null) {
                    cryptoExecutor.execute(() ->
                            recoverInBackground(cached, pukChars, newPasswordChars, repo));
                } else {
                    setLoading(false);
                    Toast.makeText(RecoverVaultActivity.this,
                            "Sin conexión y sin datos locales. Conéctate a internet.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Ejecuta el proceso criptográfico de recuperación en un hilo de fondo.
     *
     * FIX CRÍTICO — Regeneración del PUK:
     * El flujo anterior solo actualizaba la Caja A. Eso dejaba la Caja B
     * (cifrada con el PUK viejo) intacta en Firebase, permitiendo que el PUK
     * original siguiera siendo válido indefinidamente.
     *
     * Ahora:
     *   1. Se abre Caja B con el PUK viejo → DEK.
     *   2. Se genera un nuevo PUK aleatorio.
     *   3. Se deriva KEK-PUK-nuevo del nuevo PUK.
     *   4. Se cifra la DEK con KEK-PUK-nuevo → nueva Caja B.
     *   5. Se sube nueva Caja A + nueva Caja B a Firebase.
     *   6. El nuevo PUK se muestra una sola vez al usuario.
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

            // Generar nuevo PUK y nueva Caja B
            KeyManager.NuevoPukBundle nuevoPukBundle = km.generarNuevoCajaB(
                    result.dek,
                    userData.salt
            );

            // Subir nueva Caja A + nueva Caja B atomicamente
            repo.updateCajaAyB(
                    result.nuevaCajaABase64,
                    nuevoPukBundle.cajaBBase64,
                    new VaultRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void r) {
                            SessionManager.getInstance().unlock(result.dek, result.vault);
                            mainHandler.post(() -> {
                                Toast.makeText(RecoverVaultActivity.this,
                                        "Contraseña restablecida. Guarda tu nuevo código PUK.",
                                        Toast.LENGTH_SHORT).show();
                                // Mostrar nuevo PUK antes de ir al dashboard
                                Intent i = new Intent(RecoverVaultActivity.this,
                                        ShowPukActivity.class);
                                i.putExtra("extra_puk", nuevoPukBundle.puk);
                                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                        Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(i);
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
            mainHandler.post(() -> {
                setLoading(false);
                tilPuk.setError("Código PUK incorrecto");
            });
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Si el usuario escribió el PUK sin guiones (32 hex chars), los añade.
     * Si ya los tiene, los devuelve tal cual.
     */
    private String formatearPukConGuiones(String raw) {
        // Quitar guiones existentes para normalizar
        String hex = raw.replace("-", "").toUpperCase();
        if (hex.length() != 32) return raw; // no es un PUK válido, pasar tal cual
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i += 4) {
            if (sb.length() > 0) sb.append('-');
            sb.append(hex, i, i + 4);
        }
        return sb.toString();
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