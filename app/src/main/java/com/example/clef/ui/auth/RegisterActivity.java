package com.example.clef.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.data.remote.AuthManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;

public class RegisterActivity extends AppCompatActivity {

    private AuthManager       authManager;
    private ProgressBar       progressBar;
    private MaterialButton    btnRegister;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etPasswordConfirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authManager       = new AuthManager(this, getString(R.string.default_web_client_id));
        progressBar       = findViewById(R.id.progressBar);
        btnRegister       = findViewById(R.id.btnRegister);
        etEmail           = findViewById(R.id.etEmail);
        etPassword        = findViewById(R.id.etPassword);
        etPasswordConfirm = findViewById(R.id.etPasswordConfirm);

        btnRegister.setOnClickListener(v -> {
            String email = getEmail();
            char[] passwordArray = getPassword(); // Obtiene el char[]


            if (email == null || passwordArray == null) return;

            setLoading(true);


            String passwordStr = new String(passwordArray);

            authManager.registerWithEmail(email, passwordStr, (user, error) -> {

                Arrays.fill(passwordArray, '0');


                if (user != null) {
                    authManager.sendEmailVerification((u, err) -> {
                        setLoading(false);
                        Toast.makeText(this, "Cuenta creada. Verifica tu correo.", Toast.LENGTH_LONG).show();

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
        if (email.isEmpty()) { etEmail.setError("Introduce tu correo"); return null; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { etEmail.setError("Correo no válido"); return null; }
        return email;

    }

    private char[] getPassword() {
        Editable pwdEditable = etPassword.getText();
        Editable confEditable = etPasswordConfirm.getText();

        // 1. Crear los arrays de caracteres
        char[] pwd = new char[pwdEditable != null ? pwdEditable.length() : 0];
        char[] confirm = new char[confEditable != null ? confEditable.length() : 0];

        // 2. Llenar los arrays sin pasar por Strings
        if (pwdEditable != null) pwdEditable.getChars(0, pwdEditable.length(), pwd, 0);
        if (confEditable != null) confEditable.getChars(0, confEditable.length(), confirm, 0);

        // 3. Comparar el contenido de los arrays correctamente
        if (!Arrays.equals(pwd, confirm)) {
            etPasswordConfirm.setError("Las contraseñas no coinciden");
            // Limpiar confirmación de memoria antes de salir
            Arrays.fill(confirm, '0');
            return null;
        }
        // Limpiar el array de confirmación (ya no se necesita)
        Arrays.fill(confirm, '0');
        return pwd;
    }

    private void setLoading(boolean loading) {
        progressBar .setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister .setEnabled(!loading);
    }
}