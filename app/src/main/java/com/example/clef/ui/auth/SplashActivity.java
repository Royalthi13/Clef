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

public class SplashActivity extends AppCompatActivity {

    private static final int TIMEOUT_MS = 8000;

    private AuthManager     authManager;
    private FirebaseManager firebaseManager;

    private final Handler  timeoutHandler = new Handler(Looper.getMainLooper());
    private boolean        navigated      = false;

    private final Runnable timeoutRunnable = () -> goTo(LoginActivity.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SEGURIDAD: Limpiar siempre la sesión en memoria al arrancar la app.
        // Garantiza que no hay DEK residual de un usuario anterior si la app
        // fue destruida sin pasar por signOut. Coste: ninguno — si el usuario
        // era el mismo, UnlockActivity la recuperará enseguida.
        SessionManager.getInstance().lock();

        // Comprobar integridad del dispositivo antes de continuar
        RootDetector.Result rootResult = RootDetector.check(this);
        if (rootResult.blocked) {
            setContentView(R.layout.activity_splash);
            String titulo = rootResult.isClear
                    ? "Dispositivo no seguro"
                    : "Múltiples indicios de riesgo";
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(titulo)
                    .setMessage(rootResult.reason +
                            "\n\nEl acceso ha sido bloqueado para proteger tus datos. " +
                            "Si crees que es un error, contacta con soporte.")
                    .setPositiveButton("Cerrar app", (d, w) -> finish())
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

        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        authManager.getCurrentUser().reload()
                .addOnSuccessListener(unused ->
                        firebaseManager.userHasMasterPassword()
                                .addOnSuccessListener(hasMaster -> {
                                    if (hasMaster) goTo(UnlockActivity.class);
                                    else goTo(CreateMasterActivity.class);
                                })
                                .addOnFailureListener(e -> goTo(LoginActivity.class)))
                .addOnFailureListener(e -> {
                    // El usuario fue eliminado de Firebase Auth — limpiar sesión local
                    com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                    goTo(LoginActivity.class);
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