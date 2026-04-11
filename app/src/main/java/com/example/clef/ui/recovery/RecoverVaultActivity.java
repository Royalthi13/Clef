package com.example.clef.ui.recovery;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.ui.auth.LoginActivity;
import com.example.clef.ui.setup.ShowPukActivity;
import com.example.clef.utils.BruteForceGuard;
import com.example.clef.utils.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * B-6 FIX: formatearPukConGuiones() ahora muestra un error claro si la
 * longitud del PUK es inválida, en lugar de pasar el string tal cual y
 * causar un AEADBadTagException críptico.
 *
 * C-4 FIX: clon de DEK obtenido con getDek() se zeriza en finally.
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

    private BruteForceGuard bruteForceGuard;
    private CountDownTimer  countDownTimer;

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

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (user != null) ? user.getUid() : "anon";
        bruteForceGuard = new BruteForceGuard(this, uid, "recover");
    }

    @Override
    protected void onResume() { super.onResume(); applyLockoutIfNeeded(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cryptoExecutor.shutdownNow();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    private void applyLockoutIfNeeded() {
        long remaining = bruteForceGuard.getRemainingLockoutMs();
        if (remaining <= 0) { btnRecover.setEnabled(true); tilPuk.setError(null); return; }
        startCountdown(remaining);
    }

    private void startCountdown(long remaining) {
        btnRecover.setEnabled(false);
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long ms) {
                tilPuk.setError("Demasiados intentos. Espera " + ((ms + 999) / 1000) + "s.");
            }
            @Override
            public void onFinish() { tilPuk.setError(null); btnRecover.setEnabled(true); }
        }.start();
    }

    private void onRecoverClicked() {
        if (bruteForceGuard.isLockedOut()) { applyLockoutIfNeeded(); return; }

        tilPuk.setError(null);
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);

        String rawPuk = text(etPuk).replace(" ", "");
        // B-6 FIX: validar formato antes de intentar la operación criptográfica
        String pukNormalizado = normalizePuk(rawPuk);
        if (pukNormalizado == null) {
            tilPuk.setError("Formato de PUK inválido. Debe tener 32 caracteres hexadecimales.");
            return;
        }

        String newPwd     = text(etNewPassword);
        String confirmPwd = text(etConfirmPassword);

        if (newPwd.length() < 8) {
            tilNewPassword.setError(getString(R.string.master_error_min_len));
            return;
        }
        if (!newPwd.equals(confirmPwd)) {
            tilConfirmPassword.setError(getString(R.string.master_error_mismatch));
            return;
        }

        setLoading(true);

        char[] pukChars         = pukNormalizado.toCharArray();
        char[] newPasswordChars = newPwd.toCharArray();

        VaultRepository repo = new VaultRepository(this);
        repo.loadUserData(new VaultRepository.Callback<FirebaseManager.UserData>() {
            @Override
            public void onSuccess(FirebaseManager.UserData userData) {
                if (userData == null || userData.cajaB == null) {
                    setLoading(false);
                    Toast.makeText(RecoverVaultActivity.this,
                            "No se encontraron datos de recuperación.", Toast.LENGTH_LONG).show();
                    Arrays.fill(pukChars, '\0');
                    Arrays.fill(newPasswordChars, '\0');
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
                    Arrays.fill(pukChars, '\0');
                    Arrays.fill(newPasswordChars, '\0');
                    Toast.makeText(RecoverVaultActivity.this,
                            "Sin conexión y sin datos locales.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void recoverInBackground(FirebaseManager.UserData userData,
                                     char[] pukChars,
                                     char[] newPasswordChars,
                                     VaultRepository repo) {
        try {
            KeyManager km = new KeyManager();
            KeyManager.RecoveryResult result = km.recoverWithPuk(
                    pukChars, newPasswordChars,
                    userData.salt, userData.cajaB, userData.vault);

            KeyManager.NuevoPukBundle nuevoPukBundle =
                    km.generarNuevoCajaB(result.dek, userData.salt);

            repo.updateCajaAyB(result.nuevaCajaABase64, nuevoPukBundle.cajaBBase64,
                    new VaultRepository.Callback<Void>() {
                        @Override
                        public void onSuccess(Void r) {
                            bruteForceGuard.recordSuccess();
                            SessionManager.getInstance().unlock(result.dek, result.vault);
                            mainHandler.post(() -> {
                                Toast.makeText(RecoverVaultActivity.this,
                                        "Contraseña restablecida. Guarda tu nuevo PUK.",
                                        Toast.LENGTH_SHORT).show();
                                ShowPukActivity.TempSecretHolder.set(nuevoPukBundle.puk);
                                Intent i = new Intent(RecoverVaultActivity.this,
                                        ShowPukActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                        Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(i);
                                finish();
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            SessionManager.zeroizeDekCopy(result.dek);
                            mainHandler.post(() -> {
                                setLoading(false);
                                Toast.makeText(RecoverVaultActivity.this,
                                        "Error al guardar. Inténtalo de nuevo.",
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    });
        } catch (Exception e) {
            bruteForceGuard.recordFailure();
            mainHandler.post(() -> {
                setLoading(false);
                int remaining = BruteForceGuard.MAX_ATTEMPTS - bruteForceGuard.getAttemptCount();
                if (remaining <= 0) {
                    bruteForceGuard.recordSuccess();
                    SessionManager.getInstance().lock();
                    new com.example.clef.data.remote.AuthManager(
                            RecoverVaultActivity.this,
                            getString(R.string.default_web_client_id))
                            .signOut(RecoverVaultActivity.this, () -> {
                                Intent i = new Intent(RecoverVaultActivity.this,
                                        LoginActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);
                                finish();
                            });
                    return;
                }
                tilPuk.setError("Código PUK incorrecto (" + remaining + " intentos restantes)");
                applyLockoutIfNeeded();
            });
        } finally {
            Arrays.fill(pukChars, '\0');
            Arrays.fill(newPasswordChars, '\0');
        }
    }

    /**
     * B-6 FIX: Normaliza el PUK a formato con guiones.
     * Devuelve null si la longitud es inválida, en lugar de pasar el string tal cual.
     */
    private String normalizePuk(String raw) {
        String hex = raw.replace("-", "").toUpperCase();
        if (hex.length() != 32) return null;
        // Validar que todos los caracteres son hexadecimales
        if (!hex.matches("[0-9A-F]{32}")) return null;
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