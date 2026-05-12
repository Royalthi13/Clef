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
            String email    = getEmail();
            char[] password = getPassword();
            if (email == null || password == null) return;

            setLoading(true);

            // C-2 FIX: Firebase Auth API solo acepta String, así que la conversión
            // es inevitable. Construimos la String lo más tarde posible y zericamos
            // el char[] fuente inmediatamente. La propia String de Firebase queda
            // en heap hasta que el GC actúe — limitación de la API externa.
            String firebasePwd = new String(password);
            Arrays.fill(password, '\0');

            authManager.registerWithEmail(email, firebasePwd, (user, error) -> {
                if (user != null) {
                    authManager.sendEmailVerification((u, err) -> {
                        setLoading(false);
                        Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_LONG).show();
                        authManager.signOut(this, () -> {
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        });
                    });
                } else {
                    setLoading(false);
                    String msg = error != null ? error.getMessage() : getString(R.string.register_error);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });

        findViewById(R.id.btnGoToLogin).setOnClickListener(v -> finish());
    }

    private String getEmail() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (email.isEmpty()) { etEmail.setError(getString(R.string.login_error_email)); return null; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.login_error_email_invalid));
            return null;
        }
        return email;
    }

    private char[] getPassword() {
        Editable pwdEditable  = etPassword.getText();
        Editable confEditable = etPasswordConfirm.getText();

        char[] pwd     = new char[pwdEditable  != null ? pwdEditable.length()  : 0];
        char[] confirm = new char[confEditable != null ? confEditable.length() : 0];

        if (pwdEditable  != null) pwdEditable.getChars(0,  pwdEditable.length(),  pwd,     0);
        if (confEditable != null) confEditable.getChars(0, confEditable.length(), confirm, 0);

        if (pwd.length < 6) {
            etPassword.setError(getString(R.string.login_error_password_short));
            Arrays.fill(confirm, '\0');
            return null;
        }

        if (!Arrays.equals(pwd, confirm)) {
            etPasswordConfirm.setError(getString(R.string.master_error_mismatch));
            Arrays.fill(pwd,     '\0');
            Arrays.fill(confirm, '\0');
            return null;
        }
        Arrays.fill(confirm, '\0');
        return pwd;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }
}