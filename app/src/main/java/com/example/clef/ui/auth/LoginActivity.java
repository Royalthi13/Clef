package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
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
import com.example.clef.ui.setup.CreateMasterActivity;
import com.example.clef.utils.SecurePrefs;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.functions.FirebaseFunctions;

public class LoginActivity extends AppCompatActivity {

    private AuthManager     authManager;
    private FirebaseManager firebaseManager;
    private ProgressBar     progressBar;
    private MaterialButton  btnGoogleSignIn;
    private MaterialButton  btnEmailSignIn;
    private MaterialButton  btnEmailRegister;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;

    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() != null) {
                    setLoading(true);
                    authManager.handleSignInResult(result.getData(), (user, error) -> {
                        if (user != null) {
                            checkAndNavigate();
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

        authManager     = new AuthManager(this, getString(R.string.default_web_client_id));
        firebaseManager = new FirebaseManager();

        progressBar      = findViewById(R.id.progressBar);
        btnGoogleSignIn  = findViewById(R.id.btnGoogleSignIn);
        btnEmailSignIn   = findViewById(R.id.btnEmailSignIn);
        btnEmailRegister = findViewById(R.id.btnEmailRegister);
        etEmail          = findViewById(R.id.etEmail);
        etPassword       = findViewById(R.id.etPassword);

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvForgotPassword.setOnClickListener(v -> {
            String email = getEmail();
            if (email == null) return;
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Recuperar contraseña")
                    .setMessage("Se enviará un enlace a " + email + ". Revisa también la carpeta de spam.")
                    .setPositiveButton("Enviar", (d, w) ->
                            authManager.sendPasswordReset(email, (u, e) ->
                                    Toast.makeText(this, "Enlace de recuperación enviado", Toast.LENGTH_LONG).show()))
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        btnGoogleSignIn.setOnClickListener(v -> {
            setLoading(true);
            signInLauncher.launch(authManager.getGoogleSignInIntent());
        });

        btnEmailSignIn.setOnClickListener(v -> {
            String email    = getEmail();
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
                                .setMessage("Debes verificar tu correo antes de acceder.")
                                .setPositiveButton("Reenviar", (d, w) ->
                                        authManager.signInWithEmail(email, password, (u, e) -> {
                                            if (u != null) {
                                                authManager.sendEmailVerification((v2, e2) ->
                                                        Toast.makeText(this, "Email reenviado", Toast.LENGTH_LONG).show());
                                                authManager.signOut(this, () -> {});
                                            }
                                        }))
                                .setNegativeButton("Cerrar", null)
                                .show();
                    } else {
                        checkAndNavigate();
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

    /**
     * Clave de preferencias cifradas donde se guarda el timestamp de la última
     * verificación online correcta. Permite controlar el acceso offline.
     */
    private static final String PREFS_SECURITY    = "security";
    private static final String KEY_LAST_IP_CHECK = "last_ip_check";

    /** Días máximos que se permite el acceso sin verificación online. */
    private static final long MAX_OFFLINE_DAYS = 7;
    private static final long MS_PER_DAY       = 86_400_000L;

    /**
     * Llama a la Cloud Function checkLoginIp enviando el modelo del dispositivo.
     * Si la verificación es exitosa guarda el timestamp y navega.
     * Si falla (sin red), delega en handleOfflineAccess para decidir si bloquear.
     */
    private void checkAndNavigate() {
        String device = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
        FirebaseFunctions.getInstance("us-central1")
                .getHttpsCallable("checkLoginIp")
                .call(new java.util.HashMap<String, Object>() {{ put("device", device); }})
                .addOnSuccessListener(result -> {
                    saveIpCheckTimestamp();
                    navigateAfterIpCheck();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("IPCheck", "checkLoginIp failed: " + e.getMessage(), e);
                    handleOfflineAccess();
                });
    }

    /**
     * Gestiona el acceso cuando la Cloud Function no está disponible (sin red).
     * - Sin verificación previa (primer uso): deja pasar y guarda timestamp.
     * - Verificación reciente (< MAX_OFFLINE_DAYS): deja pasar con aviso.
     * - Sin verificación durante más de MAX_OFFLINE_DAYS: bloquea el acceso.
     */
    private void handleOfflineAccess() {
        long lastCheck = SecurePrefs.get(this, PREFS_SECURITY)
                .getLong(KEY_LAST_IP_CHECK, 0L);

        if (lastCheck == 0L) {
            saveIpCheckTimestamp();
            navigateAfterIpCheck();
            return;
        }

        long daysSinceCheck = (System.currentTimeMillis() - lastCheck) / MS_PER_DAY;
        if (daysSinceCheck > MAX_OFFLINE_DAYS) {
            setLoading(false);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Verificación requerida")
                    .setMessage("No se ha podido verificar tu acceso en los últimos " + MAX_OFFLINE_DAYS + " días. Necesitas conexión a internet para continuar.")
                    .setPositiveButton("Entendido", null)
                    .setCancelable(false)
                    .show();
        } else {
            Toast.makeText(this, "Sin conexión — verificación pendiente (" + daysSinceCheck + " días)", Toast.LENGTH_LONG).show();
            navigateAfterIpCheck();
        }
    }

    /** Guarda el timestamp actual como última verificación online exitosa. */
    private void saveIpCheckTimestamp() {
        SecurePrefs.get(this, PREFS_SECURITY)
                .edit()
                .putLong(KEY_LAST_IP_CHECK, System.currentTimeMillis())
                .apply();
    }

    private void navigateAfterIpCheck() {
        firebaseManager.userHasMasterPassword()
                .addOnSuccessListener(hasMaster -> {
                    setLoading(false);
                    if (hasMaster) {
                        goTo(UnlockActivity.class);
                    } else {
                        goTo(CreateMasterActivity.class);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Error al conectar con el servidor", Toast.LENGTH_SHORT).show();
                });
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
        finish();
    }

    private String getEmail() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (email.isEmpty()) { etEmail.setError("Introduce tu correo"); return null; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { etEmail.setError("Correo no válido"); return null; }
        return email;
    }

    private String getPassword() {
        String pwd = etPassword.getText() != null ? etPassword.getText().toString() : "";
        if (pwd.length() < 6) { etPassword.setError("Mínimo 6 caracteres"); return null; }
        return pwd;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnGoogleSignIn .setEnabled(!loading);
        btnEmailSignIn  .setEnabled(!loading);
        btnEmailRegister.setEnabled(!loading);
    }
}