package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.ui.dashboard.MainActivity;
import com.example.clef.ui.setup.CreateMasterActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterActivity extends AppCompatActivity {

    private AuthManager authManager;
    private FirebaseManager firebaseManager;
    private ProgressBar progressBar;
    private MaterialButton btnRegister;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etPasswordConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authManager = new AuthManager(this, getString(R.string.default_web_client_id));
        firebaseManager = new FirebaseManager();

        progressBar = findViewById(R.id.progressBar);
        btnRegister = findViewById(R.id.btnRegister);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm);

        btnRegister.setOnClickListener(v -> {
            String email = getEmail();
            String password = getPassword();
            if (email == null || password == null) return;

            setLoading(true);
            authManager.registerWithEmail(email, password, (user, error) -> {
                if (user != null) {
                    authManager.sendEmailVerification((u, err) -> {
                        setLoading(false);
                        Toast.makeText(this,
                                "Cuenta creada. Verifica tu correo antes de iniciar sesión.\nRevisa también la carpeta de spam.",
                                Toast.LENGTH_LONG).show();
                        authManager.signOut(this, () -> {
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        });
                    });
                } else {
                    setLoading(false);
                    String msg = error != null ? error.getMessage() : "Error al crear la cuenta";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });

        findViewById(R.id.btnGoToLogin).setOnClickListener(v -> finish());
    }

    private String getEmail() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (email.isEmpty()) {
            etEmail.setError("Introduce tu correo");
            return null;
        }
        return email;
    }

    private String getPassword() {
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirm = etPasswordConfirm.getText() != null ? etPasswordConfirm.getText().toString() : "";

        if (password.length() < 6) {
            etPassword.setError("Mínimo 6 caracteres");
            return null;
        }
        if (!password.equals(confirm)) {
            etPasswordConfirm.setError("Las contraseñas no coinciden");
            return null;
        }
        return password;
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
        btnRegister.setEnabled(!loading);
    }
}
