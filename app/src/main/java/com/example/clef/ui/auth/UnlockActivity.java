package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Vault;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.ui.dashboard.MainActivity;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla de desbloqueo que aparece cuando el usuario ya está autenticado
 * en Firebase pero la sesión está bloqueada (no hay DEK en memoria).
 *
 * Flujo:
 *   1. Carga salt + cajaA + vault desde Firebase (o caché local si hay sin red).
 *   2. Si la biometría está activada, lanza el prompt biométrico automáticamente.
 *   3. Si el usuario cancela la biometría (o no la tiene activada), muestra
 *      el campo de contraseña maestra.
 *   4. Al autenticarse correctamente, guarda la DEK en SessionManager y va a MainActivity.
 */
public class UnlockActivity extends AppCompatActivity {

    private TextInputLayout  tilPassword;
    private TextInputEditText etPassword;
    private MaterialButton   btnUnlock;
    private MaterialButton   btnBiometric;
    private View             loadingOverlay;

    private FirebaseManager.UserData userData;

    private final ExecutorService cryptoExecutor = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler    = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock);

        tilPassword    = findViewById(R.id.tilMasterPassword);
        etPassword     = findViewById(R.id.etMasterPassword);
        btnUnlock      = findViewById(R.id.btnUnlock);
        btnBiometric   = findViewById(R.id.btnBiometric);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        btnUnlock.setOnClickListener(v -> onMasterPasswordSubmit());

        setLoading(true);
        loadUserData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cryptoExecutor.shutdownNow();
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
            setLoading(false);
            Toast.makeText(this,
                    "No hay datos locales. Usa Importar en Ajustes.",
                    Toast.LENGTH_LONG).show();
            goTo(LoginActivity.class);
        }
    }

    /** Configura la UI y, si la biometría está activa, la lanza automáticamente. */
    private void onDataReady() {
        boolean bioAvailable = BiometricHelper.isAvailable(this)
                && BiometricHelper.isEnabled(this);

        if (bioAvailable) {
            btnBiometric.setVisibility(View.VISIBLE);
            btnBiometric.setOnClickListener(v -> launchBiometric());
            // Lanzamos el prompt automáticamente al cargar la pantalla
            launchBiometric();
        } else {
            btnBiometric.setVisibility(View.GONE);
        }
    }


    // ── Autenticación biométrica ───────────────────────────────────────────────

    private void launchBiometric() {
        BiometricHelper.unlock(this, new BiometricHelper.UnlockCallback() {
            @Override
            public void onSuccess(byte[] dek) {
                // Con biometría tenemos la DEK pero no el Vault descifrado todavía.
                // userData ya está cargado — solo necesitamos descifrar el vault con la DEK.
                cryptoExecutor.execute(() -> {
                    try {
                        KeyManager km = new KeyManager();
                        Vault vault = km.descifrarVault(userData.vault, dek);
                        SessionManager.getInstance().unlock(dek, vault);
                        mainHandler.post(UnlockActivity.this::goToMain);
                    } catch (Exception e) {
                        mainHandler.post(() ->
                                Toast.makeText(UnlockActivity.this,
                                        "Error al descifrar la bóveda", Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(String message) {
                // La biometría falló o fue invalidada — el campo de contraseña sigue disponible
                Toast.makeText(UnlockActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled() {
                // El usuario pulsó "Usar contraseña" — no hacemos nada, el campo ya está visible
            }
        });
    }


    // ── Autenticación con contraseña maestra ───────────────────────────────────

    private void onMasterPasswordSubmit() {
        if (userData == null) {
            Toast.makeText(this, getString(R.string.unlock_no_data), Toast.LENGTH_SHORT).show();
            return;
        }

        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.master_error_required));
            return;
        }
        tilPassword.setError(null);
        setLoading(true);

        // Copiamos los datos antes de entrar al hilo: los Strings son inmutables, está bien
        char[] passwordChars = password.toCharArray();
        String salt  = userData.salt;
        String cajaA = userData.cajaA;
        String vault = userData.vault;

        cryptoExecutor.execute(() -> {
            try {
                KeyManager keyManager = new KeyManager();
                KeyManager.LoginResult result = keyManager.login(passwordChars, salt, cajaA, vault);

                SessionManager.getInstance().unlock(result.dek, result.vault);
                mainHandler.post(this::goToMain);

            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    tilPassword.setError(getString(R.string.unlock_wrong_password));
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
        btnUnlock.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        btnBiometric.setEnabled(!loading);
    }
}
