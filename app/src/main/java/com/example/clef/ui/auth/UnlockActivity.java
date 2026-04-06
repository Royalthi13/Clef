package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Vault;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.ui.dashboard.MainActivity;
import com.example.clef.ui.recovery.RecoverVaultActivity;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.BruteForceGuard;
import com.example.clef.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UnlockActivity extends AppCompatActivity {

    private TextInputLayout   tilPassword;
    private TextInputEditText etPassword;
    private MaterialButton    btnUnlock;
    private MaterialButton    btnBiometric;
    private View              loadingOverlay;

    private FirebaseManager.UserData userData;
    private BruteForceGuard          bruteForceGuard;
    private CountDownTimer           countDownTimer;

    private final ExecutorService cryptoExecutor = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler    = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock);

        if (getIntent().getBooleanExtra("session_expired", false)) {
            ((android.widget.TextView) findViewById(R.id.tvUnlockTitle))
                    .setText("Tu sesión ha expirado");
        }

        tilPassword    = findViewById(R.id.tilMasterPassword);
        etPassword     = findViewById(R.id.etMasterPassword);
        btnUnlock      = findViewById(R.id.btnUnlock);
        btnBiometric   = findViewById(R.id.btnBiometric);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String uid = (user != null) ? user.getUid() : "anon";
        bruteForceGuard = new BruteForceGuard(this, uid, "unlock");

        btnUnlock.setOnClickListener(v -> onMasterPasswordSubmit());

        TextView tvForgotMaster = findViewById(R.id.tvForgotMasterPassword);
        if (tvForgotMaster != null) {
            tvForgotMaster.setOnClickListener(v ->
                    startActivity(new Intent(this, RecoverVaultActivity.class)));
        }

        setLoading(true);
        loadUserData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyLockoutIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cryptoExecutor.shutdownNow();
        if (countDownTimer != null) countDownTimer.cancel();
    }

    // ── Protección fuerza bruta ────────────────────────────────────────────────

    private void applyLockoutIfNeeded() {
        long remaining = bruteForceGuard.getRemainingLockoutMs();
        if (remaining <= 0) {
            btnUnlock.setEnabled(true);
            tilPassword.setError(null);
            return;
        }
        startCountdown(remaining);
    }

    private void startCountdown(long remaining) {
        btnUnlock.setEnabled(false);
        if (countDownTimer != null) countDownTimer.cancel();
        countDownTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long s = (millisUntilFinished + 999) / 1000;
                tilPassword.setError("Demasiados intentos. Espera " + s + "s.");
            }
            @Override
            public void onFinish() {
                tilPassword.setError(null);
                btnUnlock.setEnabled(true);
            }
        }.start();
    }

    // ── Carga de datos ─────────────────────────────────────────────────────────

    private void loadUserData() {
        VaultRepository repo = new VaultRepository(this);
        FirebaseManager.UserData cached = repo.loadOfflineUserData();

        if (cached != null) {
            userData = cached;
            setLoading(false);
            onDataReady();
        } else {
            repo.downloadAndCacheFromFirebase(new VaultRepository.Callback<FirebaseManager.UserData>() {
                @Override
                public void onSuccess(FirebaseManager.UserData data) {
                    if (data != null) {
                        userData = data;
                        setLoading(false);
                        onDataReady();
                    } else {
                        setLoading(false);
                        showNoDataError();
                    }
                }

                @Override
                public void onError(Exception e) {
                    setLoading(false);
                    Toast.makeText(UnlockActivity.this,
                            "Sin conexión y sin datos locales. Conéctate a internet para continuar.",
                            Toast.LENGTH_LONG).show();
                    btnUnlock.setEnabled(false);
                    btnUnlock.setText("Sin datos disponibles");
                }
            });
        }
    }

    private void showNoDataError() {
        Toast.makeText(this, getString(R.string.unlock_no_data), Toast.LENGTH_LONG).show();
        goTo(LoginActivity.class);
    }

    private void onDataReady() {
        applyLockoutIfNeeded();
        boolean bioAvailable = BiometricHelper.isAvailable(this) && BiometricHelper.isEnabled(this);
        if (bioAvailable) {
            btnBiometric.setVisibility(View.VISIBLE);
            btnBiometric.setOnClickListener(v -> launchBiometric());
            launchBiometric();
        } else {
            btnBiometric.setVisibility(View.GONE);
        }
    }

    // ── Biometría ──────────────────────────────────────────────────────────────

    private void launchBiometric() {
        BiometricHelper.unlock(this, new BiometricHelper.UnlockCallback() {
            @Override
            public void onSuccess(byte[] dek) {
                cryptoExecutor.execute(() -> {
                    try {
                        KeyManager km    = new KeyManager();
                        Vault      vault = km.descifrarVault(userData.vault, dek);
                        bruteForceGuard.recordSuccess();
                        SessionManager.getInstance().unlock(dek, vault);
                        mainHandler.post(UnlockActivity.this::goToMain);
                    } catch (Exception e) {
                        mainHandler.post(() ->
                                Toast.makeText(UnlockActivity.this,
                                        "Error al descifrar la bóveda", Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override public void onError(String message) {
                Toast.makeText(UnlockActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override public void onCancelled() {}
        });
    }

    // ── Contraseña maestra ─────────────────────────────────────────────────────

    private void onMasterPasswordSubmit() {
        if (userData == null) {
            Toast.makeText(this, getString(R.string.unlock_no_data), Toast.LENGTH_SHORT).show();
            return;
        }

        if (bruteForceGuard.isLockedOut()) {
            applyLockoutIfNeeded();
            return;
        }

        Editable editable = etPassword.getText();
        char[] passwordChars = new char[editable != null ? editable.length() : 0];
        if (editable != null) editable.getChars(0, editable.length(), passwordChars, 0);
        if (passwordChars.length == 0) {
            tilPassword.setError(getString(R.string.master_error_required));
            return;
        }
        tilPassword.setError(null);
        setLoading(true);

        String salt  = userData.salt;
        String cajaA = userData.cajaA;
        String vault = userData.vault;

        cryptoExecutor.execute(() -> {
            try {
                KeyManager.LoginResult result =
                        new KeyManager().login(passwordChars, salt, cajaA, vault);
                bruteForceGuard.recordSuccess();
                SessionManager.getInstance().unlock(result.dek, result.vault);
                mainHandler.post(this::goToMain);
            } catch (Exception e) {
                bruteForceGuard.recordFailure();
                mainHandler.post(() -> {
                    setLoading(false);
                    int remaining = BruteForceGuard.MAX_ATTEMPTS - bruteForceGuard.getAttemptCount();
                    if (remaining <= 0) {
                        bruteForceGuard.recordSuccess();
                        startActivity(new Intent(UnlockActivity.this, RecoverVaultActivity.class));
                        finish();
                        return;
                    }
                    tilPassword.setError(getString(R.string.unlock_wrong_password)
                            + " (" + remaining + " intentos restantes)");
                    applyLockoutIfNeeded();
                });
            }
        });
    }

    // ── Navegación ─────────────────────────────────────────────────────────────

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
        finish();
    }

    // ── UI helpers ─────────────────────────────────────────────────────────────

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnUnlock   .setEnabled(!loading);
        btnBiometric.setEnabled(!loading);
        etPassword  .setEnabled(!loading);
    }
}
