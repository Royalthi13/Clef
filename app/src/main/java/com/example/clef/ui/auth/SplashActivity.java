package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.ui.setup.CreateMasterActivity;
import com.example.clef.utils.RootDetector;
import com.example.clef.utils.SessionManager;

/**
 * B-7 FIX: El timeout de 8s se amplía a 15s para redes lentas y se muestra
 * un mensaje explicativo en lugar de redirigir silenciosamente a LoginActivity.
 * Además, si el timeout expira, se muestra un diálogo de reintento en lugar
 * de perder la sesión del usuario autenticado.
 */
public class SplashActivity extends AppCompatActivity {

    // B-7 FIX: 15s en lugar de 8s. Redes lentas (ej. 3G) necesitan más tiempo.
    private static final int TIMEOUT_MS = 15_000;

    private AuthManager     authManager;
    private FirebaseManager firebaseManager;

    private final Handler  timeoutHandler = new Handler(Looper.getMainLooper());
    private boolean        navigated      = false;

    private final Runnable timeoutRunnable = () -> {
        // B-7 FIX: si hay usuario autenticado, ofrecer reintento en lugar de logout forzado
        if (authManager != null && authManager.getCurrentUser() != null) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.splash_no_connection_title))
                    .setMessage(getString(R.string.splash_no_connection_message))
                    .setPositiveButton(getString(R.string.btn_retry), (d, w) -> {
                        navigated = false;
                        startFirebaseCheck();
                    })
                    .setNegativeButton(getString(R.string.splash_use_offline), (d, w) -> goTo(LoginActivity.class))
                    .setCancelable(false)
                    .show();
        } else {
            goTo(LoginActivity.class);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager.getInstance().lock();

        RootDetector.Result rootResult = RootDetector.check(this);
        if (rootResult.blocked) {
            setContentView(R.layout.activity_splash);
            String titulo = rootResult.isClear ? getString(R.string.splash_root_clear_title)
                    : getString(R.string.splash_root_risk_title);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(titulo)
                    .setMessage(rootResult.reason + getString(R.string.splash_root_blocked_suffix))
                    .setPositiveButton(getString(R.string.splash_close_app), (d, w) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }

        setContentView(R.layout.activity_splash);

        authManager     = new AuthManager(this, getString(R.string.default_web_client_id));
        firebaseManager = new FirebaseManager();

        if (authManager.getCurrentUser() == null) {
            goTo(LoginActivity.class);
            return;
        }

        startFirebaseCheck();
    }

    private void startFirebaseCheck() {
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        authManager.getCurrentUser().reload()
                .addOnSuccessListener(unused ->
                        firebaseManager.userHasMasterPassword()
                                .addOnSuccessListener(hasMaster -> {
                                    if (hasMaster) goTo(UnlockActivity.class);
                                    else goTo(CreateMasterActivity.class);
                                })
                                .addOnFailureListener(e -> goTo(UnlockActivity.class)))
                .addOnFailureListener(e -> {
                    // Sin red: no cerrar sesión, ir a desbloqueo con datos locales
                    goTo(UnlockActivity.class);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timeoutHandler.removeCallbacks(timeoutRunnable);
    }

    private void goTo(Class<?> destination) {
        if (navigated) return;
        navigated = true;
        timeoutHandler.removeCallbacks(timeoutRunnable);
        startActivity(new Intent(this, destination));
        finish();
    }
}