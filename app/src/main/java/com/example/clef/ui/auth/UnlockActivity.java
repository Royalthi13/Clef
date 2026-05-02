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
import com.example.clef.data.remote.AuthManager;
import com.example.clef.utils.AccountBlocker;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.BruteForceGuard;
import com.example.clef.utils.SecurePrefs;
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
    private MaterialButton    btnSwitchAccount;
    private View              loadingOverlay;

    private FirebaseManager.UserData userData;
    private BruteForceGuard          bruteForceGuard;
    private CountDownTimer           countDownTimer;
    private String                   uid;

    private static final long   BIOMETRIC_PWD_INTERVAL_MS = 3 * 24 * 60 * 60 * 1000L;
    private static final String PREFS_BIO_CHECK           = "bio_pwd_check";
    private static final String KEY_LAST_PWD              = "last_pwd_";

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

        tilPassword      = findViewById(R.id.tilMasterPassword);
        etPassword       = findViewById(R.id.etMasterPassword);
        btnUnlock        = findViewById(R.id.btnUnlock);
        btnBiometric     = findViewById(R.id.btnBiometric);
        btnSwitchAccount = findViewById(R.id.btnSwitchAccount);
        loadingOverlay   = findViewById(R.id.loadingOverlay);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        uid = (user != null) ? user.getUid() : "anon";
        bruteForceGuard = new BruteForceGuard(this, uid, "unlock");

        if (user != null && AccountBlocker.isBlocked(this, uid)) {
            showBlockedDialog();
            return;
        }

        btnUnlock.setOnClickListener(v -> onMasterPasswordSubmit());
        btnSwitchAccount.setOnClickListener(v -> switchAccount());

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
        if (bioAvailable && !isPasswordCheckDue()) {
            btnBiometric.setVisibility(View.VISIBLE);
            btnBiometric.setOnClickListener(v -> launchBiometric());
            launchBiometric();
        } else {
            btnBiometric.setVisibility(View.GONE);
            if (bioAvailable) {
                tilPassword.setHelperText(
                        "Por seguridad, introduce tu contraseña maestra cada 3 días.");
            }
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
                mainHandler.post(() -> {
                    savePasswordCheckTimestamp();
                    goToMain();
                });
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

    private void switchAccount() {
        SessionManager.getInstance().lock();
        new AuthManager(this, getString(R.string.default_web_client_id))
                .signOut(this, () -> goTo(LoginActivity.class));
    }

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
        btnUnlock       .setEnabled(!loading);
        btnBiometric    .setEnabled(!loading);
        btnSwitchAccount.setEnabled(!loading);
        etPassword      .setEnabled(!loading);
    }

    // ── Verificación periódica de contraseña con biometría activa ─────────────

    /**
     * Devuelve true si han pasado más de 3 días desde la última vez que el usuario
     * introdujo su contraseña maestra. Fuerza una verificación periódica aunque
     * la biometría esté habilitada.
     */
    private boolean isPasswordCheckDue() {
        long last = SecurePrefs.get(this, PREFS_BIO_CHECK)
                .getLong(KEY_LAST_PWD + uid, 0L);
        return System.currentTimeMillis() - last >= BIOMETRIC_PWD_INTERVAL_MS;
    }

    /** Guarda el timestamp actual como última verificación de contraseña maestra. */
    private void savePasswordCheckTimestamp() {
        SecurePrefs.get(this, PREFS_BIO_CHECK)
                .edit()
                .putLong(KEY_LAST_PWD + uid, System.currentTimeMillis())
                .apply();
    }

    /**
     * Muestra el diálogo informativo de cuenta bloqueada y cierra sesión al aceptar.
     * No se puede cancelar: el usuario debe pulsar "Entendido" para continuar.
     */
    private void showBlockedDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Cuenta bloqueada")
                .setMessage("Tu cuenta está bloqueada.\n\n" +
                        "Deberás ponerte en contacto con el servicio técnico en un plazo " +
                        "máximo de 3 meses para recuperar tu cuenta. Pasado ese plazo, " +
                        "tu cuenta será eliminada de forma permanente.\n\n" +
                        "Contacto: serviciotecnico@ejemplo.clef")
                .setPositiveButton("Entendido", (d, w) ->
                        new AuthManager(this, getString(R.string.default_web_client_id))
                                .signOut(this, () -> goTo(LoginActivity.class)))
                .setCancelable(false)
                .show();
    }
}
