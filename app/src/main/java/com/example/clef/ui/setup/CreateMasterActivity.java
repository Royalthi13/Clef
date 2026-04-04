package com.example.clef.ui.setup;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.ui.auth.LoginActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CreateMasterActivity extends AppCompatActivity {

    public static final String EXTRA_PUK = "extra_puk";

    private TextInputLayout            tilMaster;
    private TextInputLayout            tilConfirm;
    private TextInputEditText          etMaster;
    private TextInputEditText          etConfirm;
    private LinearProgressIndicator    strengthBar;
    private TextView                   tvStrength;
    private MaterialButton             btnCreate;
    private View                       loadingOverlay;

    private final ExecutorService cryptoExecutor = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler    = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_master);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tilMaster      = findViewById(R.id.tilMasterPassword);
        tilConfirm     = findViewById(R.id.tilConfirmPassword);
        etMaster       = findViewById(R.id.etMasterPassword);
        etConfirm      = findViewById(R.id.etConfirmPassword);
        strengthBar    = findViewById(R.id.strengthBar);
        tvStrength     = findViewById(R.id.tvStrength);
        btnCreate      = findViewById(R.id.btnCreateVault);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateStrengthUi();
                clearErrorsIfPossible();
            }
        };
        etMaster.addTextChangedListener(watcher);
        etConfirm.addTextChangedListener(watcher);

        btnCreate.setOnClickListener(v -> onCreateVault());
        // FIX: Mostrar estado inicial útil en lugar de "Fortaleza" estático
        tvStrength.setText("Introduce una contraseña para ver su fortaleza");
        strengthBar.setProgressCompat(0, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cryptoExecutor.shutdownNow();
    }

    private void onCreateVault() {
        String master  = etMaster.getText()  != null ? etMaster.getText().toString()  : "";
        String confirm = etConfirm.getText() != null ? etConfirm.getText().toString() : "";

        tilMaster.setError(null);
        tilConfirm.setError(null);

        if (master.isEmpty()) {
            tilMaster.setError(getString(R.string.master_error_required));
            return;
        }
        if (master.length() < 8) {
            tilMaster.setError(getString(R.string.master_error_min_len));
            return;
        }
        if (!master.equals(confirm)) {
            tilConfirm.setError(getString(R.string.master_error_mismatch));
            return;
        }

        setLoading(true);

        char[] masterChars = master.toCharArray();
        cryptoExecutor.execute(() -> {
            try {
                KeyManager keyManager = new KeyManager();
                KeyManager.RegistrationBundle bundle = keyManager.register(masterChars);

                VaultRepository repo = new VaultRepository(this);
                repo.registerUser(bundle, new VaultRepository.Callback<Void>() {
                    @Override public void onSuccess(Void result) {
                        com.example.clef.utils.SessionManager.getInstance()
                                .unlock(bundle.dek, new com.example.clef.data.model.Vault());
                        mainHandler.post(() -> {
                            setLoading(false);
                            Intent i = new Intent(CreateMasterActivity.this, ShowPukActivity.class);
                            i.putExtra(EXTRA_PUK, bundle.puk);
                            startActivity(i);
                            finish();
                        });
                    }

                    @Override public void onError(Exception e) {
                        mainHandler.post(() -> {
                            setLoading(false);
                            Toast.makeText(CreateMasterActivity.this,
                                    R.string.master_error_register_failed, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    Toast.makeText(this,
                            R.string.master_error_crypto_failed, Toast.LENGTH_LONG).show();
                    if (e.getMessage() != null &&
                            e.getMessage().toLowerCase(Locale.ROOT).contains("auth")) {
                        startActivity(new Intent(this, LoginActivity.class));
                        finish();
                    }
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnCreate.setEnabled(!loading);
        etMaster .setEnabled(!loading);
        etConfirm.setEnabled(!loading);
    }

    private void updateStrengthUi() {
        String pwd = etMaster.getText() != null ? etMaster.getText().toString() : "";

        if (pwd.isEmpty()) {
            tvStrength.setText("Introduce una contraseña para ver su fortaleza");
            strengthBar.setProgressCompat(0, true);
            return;
        }

        Strength s = Strength.estimate(pwd);
        strengthBar.setProgressCompat(s.score, true);
        tvStrength.setText(getString(R.string.master_strength_format, s.label, s.score));
    }

    private void clearErrorsIfPossible() {
        if (tilMaster.getError()  != null) tilMaster.setError(null);
        if (tilConfirm.getError() != null) tilConfirm.setError(null);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FIX: Strength.estimate() reescrito sin regex para no penalizar cada tecla.
    // Antes usaba p.matches(".*[a-z].*") x4, que compila y evalúa una regex
    // completa por pulsación. Ahora itera una vez sobre los chars del String.
    // ──────────────────────────────────────────────────────────────────────────
    private enum Strength {
        WEAK("Débil", 20),
        OK("Aceptable", 50),
        STRONG("Fuerte", 75),
        EXCELLENT("Excelente", 90);

        final String label;
        final int    score;

        Strength(String label, int score) {
            this.label = label;
            this.score = score;
        }

        static Strength estimate(String p) {
            if (p == null || p.isEmpty()) return WEAK;

            int  len    = p.length();
            boolean lower  = false;
            boolean upper  = false;
            boolean digit  = false;
            boolean symbol = false;

            for (int i = 0; i < len; i++) {
                char c = p.charAt(i);
                if      (c >= 'a' && c <= 'z') lower  = true;
                else if (c >= 'A' && c <= 'Z') upper  = true;
                else if (c >= '0' && c <= '9') digit  = true;
                else                           symbol = true;

                // Early exit si ya encontramos todo
                if (lower && upper && digit && symbol) break;
            }

            int variety = (lower ? 1 : 0) + (upper ? 1 : 0)
                    + (digit ? 1 : 0) + (symbol ? 1 : 0);

            if (len >= 14 && variety >= 3) return EXCELLENT;
            if (len >= 12 && variety >= 3) return STRONG;
            if (len >=  8 && variety >= 2) return OK;
            return WEAK;
        }
    }
}