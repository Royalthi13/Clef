package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private AuthManager authManager;
    private FirebaseManager firebaseManager;
    private ProgressBar progressBar;
    private MaterialButton btnGoogleSignIn;
    private MaterialButton btnEmailSignIn;
    private MaterialButton btnEmailRegister;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;

    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() != null) {
                    setLoading(true);
                    authManager.handleSignInResult(result.getData(), (user, error) -> {
                        if (user != null) {
                            checkUserAndNavigate();
                        } else {
                            setLoading(false);
                            Toast.makeText(this, "Error al iniciar sesión con Google", Toast.LENGTH_SHORT).show();
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
        btnEmailSignIn = findViewById(R.id.btnEmailSignIn);
        btnEmailRegister = findViewById(R.id.btnEmailRegister);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvForgotPassword.setOnClickListener(v -> {
            String email = getEmail();
            if (email == null) return;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Recuperar contraseña")
                    .setMessage("¿Desea recuperar su contraseña? Se enviará un enlace a " + email + ".\n\nRevisa también la carpeta de spam.")
                    .setPositiveButton("Sí", (dialog, which) ->
                            authManager.sendPasswordReset(email, (user, error) -> {
                                Toast.makeText(this,
                                        "Mensaje de recuperación enviado",
                                        Toast.LENGTH_LONG).show();
                            })
                    )
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        btnGoogleSignIn.setOnClickListener(v -> {
            setLoading(true);
            signInLauncher.launch(authManager.getGoogleSignInIntent());
        });

        btnEmailSignIn.setOnClickListener(v -> {
            String email = getEmail();
            String password = getPassword();
            if (email == null || password == null) return;

            setLoading(true);
            authManager.signInWithEmail(email, password, (user, error) -> {
                if (user != null) {
                    if (!authManager.isEmailVerified()) {
                        setLoading(false);
                        authManager.signOut(this, () -> {});
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Email no verificado")
                                .setMessage("Debes verificar tu correo antes de acceder. Revisa tu bandeja de entrada y la carpeta de spam.")
                                .setPositiveButton("Reenviar email", (dialog, which) ->
                                        authManager.signInWithEmail(email, password, (u, e) -> {
                                            if (u != null) {
                                                authManager.sendEmailVerification((v2, e2) ->
                                                        Toast.makeText(this, "Email de verificación reenviado", Toast.LENGTH_LONG).show()
                                                );
                                                authManager.signOut(this, () -> {});
                                            }
                                        })
                                )
                                .setNegativeButton("Cerrar", null)
                                .show();
                    } else {
                        checkUserAndNavigate();
                    }
                } else {
                    setLoading(false);
                    Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnEmailRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
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
        if (password.length() < 6) {
            etPassword.setError("Mínimo 6 caracteres");
            return null;
        }
        return password;
    }

    private void checkUserAndNavigate() {
        firebaseManager.userExists().addOnSuccessListener(exists -> {
            if (exists) {
                goTo(UnlockActivity.class);
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
        btnEmailSignIn.setEnabled(!loading);
        btnEmailRegister.setEnabled(!loading);
    }
}
