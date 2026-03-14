package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.ui.dashboard.MainActivity;
import com.example.clef.ui.setup.CreateMasterActivity;
import com.google.android.material.button.MaterialButton;

// LoginActivity muestra el botón de Google Sign-In.
// Tras autenticarse, comprueba si el usuario ya existe en Firestore para decidir
// si va a crear la bóveda por primera vez o a desbloquearla.
//Losiento por tocarlo pero lo necesitaba para ver la BBDD de Firebase

public class LoginActivity extends AppCompatActivity {

    private AuthManager authManager;
    private FirebaseManager firebaseManager;
    private ProgressBar progressBar;
    private MaterialButton btnGoogleSignIn;

    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() != null) {
                    setLoading(true);
                    authManager.handleSignInResult(result.getData(), (user, error) -> {
                        if (user != null) {
                            checkUserAndNavigate();
                        } else {
                            setLoading(false);
                            Toast.makeText(this, "Error al iniciar sesión", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = new AuthManager(this, getString(R.string.default_web_client_id));
        firebaseManager = new FirebaseManager();

        progressBar = findViewById(R.id.progressBar);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);

        btnGoogleSignIn.setOnClickListener(v -> {
            setLoading(true);
            signInLauncher.launch(authManager.getGoogleSignInIntent());
        });
    }

    private void checkUserAndNavigate() {
        firebaseManager.userExists().addOnSuccessListener(exists -> {
            if (exists) {
                goTo(MainActivity.class);
            } else {
                goTo(CreateMasterActivity.class);
            }
        }).addOnFailureListener(e -> {
            setLoading(false);
            Toast.makeText(this, "Error al conectar con el servidor", Toast.LENGTH_SHORT).show();
        });
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnGoogleSignIn.setEnabled(!loading);
    }
}
