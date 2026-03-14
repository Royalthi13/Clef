package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.ui.dashboard.MainActivity;
import com.example.clef.ui.setup.CreateMasterActivity;

// SplashActivity es el punto de entrada real de la app.
// Comprueba si hay sesión de Firebase activa y si el usuario ya existe en Firestore,
// y redirige a la pantalla correcta sin que el usuario tenga que hacer nada.

public class SplashActivity extends AppCompatActivity {

    private AuthManager authManager;
    private FirebaseManager firebaseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        authManager = new AuthManager(this, getString(R.string.default_web_client_id));
        firebaseManager = new FirebaseManager();

        if (authManager.getCurrentUser() == null) {
            // Sin sesión → ir a Login
            goTo(LoginActivity.class);
        } else {
            // Con sesión → comprobar si ya tiene bóveda en Firestore
            firebaseManager.userExists().addOnSuccessListener(exists -> {
                if (exists) {
                    goTo(MainActivity.class); // usuario conocido → desbloquear bóveda
                } else {
                    goTo(CreateMasterActivity.class); // usuario nuevo → crear Master Password
                }
            }).addOnFailureListener(e -> {
                // Sin conexión → ir a Login para que reintente
                goTo(LoginActivity.class);
            });
        }
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
        finish();
    }
}
