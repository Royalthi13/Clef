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

public class SplashActivity extends AppCompatActivity {

    private static final int TIMEOUT_MS = 8000;

    private AuthManager authManager;
    private FirebaseManager firebaseManager;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private boolean navigated = false;

    private final Runnable timeoutRunnable = () -> {
        // Si Firebase no responde en 8 segundos, mandamos al Login
        goTo(LoginActivity.class);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        authManager = new AuthManager(this, getString(R.string.default_web_client_id));
        firebaseManager = new FirebaseManager();

        if (authManager.getCurrentUser() == null) {
            goTo(LoginActivity.class);
        } else {
            timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

            firebaseManager.userExists()
                    .addOnSuccessListener(exists -> {
                        if (exists) {
                            goTo(UnlockActivity.class);
                        } else {
                            goTo(CreateMasterActivity.class);
                        }
                    })
                    .addOnFailureListener(e -> goTo(LoginActivity.class));
        }
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
