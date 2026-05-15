package com.example.clef.ui.setup;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.ui.dashboard.MainActivity;
import com.example.clef.utils.ClipboardHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Arrays;

public class ShowPukActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_puk);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Consumir char[] y zeriarlo inmediatamente.
        // Antes era String static que quedaba indefinidamente en heap.
        char[] pukChars = TempSecretHolder.getAndClear();
        if (pukChars == null || pukChars.length == 0) {
            Toast.makeText(this, R.string.puk_missing, Toast.LENGTH_LONG).show();
            goToMain();
            return;
        }
        TextView tvPuk = findViewById(R.id.tvPukCode);
        String pukDisplay = new String(pukChars);
        tvPuk.setText(pukDisplay);
        Arrays.fill(pukChars, '\0');




        setupPukTip();

        MaterialButton btnCopy     = findViewById(R.id.btnCopyPuk);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);

        btnCopy.setOnClickListener(v -> {
            ClipboardHelper.copySensitivePuk(this, "PUK", pukDisplay);
            Toast.makeText(this, R.string.puk_copied, Toast.LENGTH_SHORT).show();
        });

        btnContinue.setOnClickListener(v -> goToMain());
    }

    private void setupPukTip() {
        com.google.firebase.auth.FirebaseUser tipUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String tipUid = tipUser != null ? tipUser.getUid() : "anon";
        android.content.SharedPreferences tipPrefs =
                getSharedPreferences("tip_prefs_" + tipUid, MODE_PRIVATE);
        android.view.View cardPukTip = findViewById(R.id.cardPukTip);
        if (cardPukTip == null) return;
        if (tipPrefs.getBoolean("tip_dismissed_puk", false)) {
            cardPukTip.setVisibility(android.view.View.GONE);
        } else {
            android.view.View btnDismiss = findViewById(R.id.btnPukTipDismiss);
            if (btnDismiss != null) {
                btnDismiss.setOnClickListener(v -> {
                    tipPrefs.edit().putBoolean("tip_dismissed_puk", true).apply();
                    cardPukTip.animate().alpha(0f).setDuration(250)
                            .withEndAction(() -> cardPukTip.setVisibility(android.view.View.GONE)).start();
                });
            }
        }
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    /**
     * Holder temporal del PUK usando char[] en lugar de String estático.
     * El array se zeriza al leer y al sobrescribir.
     */
    public static class TempSecretHolder {
        private static volatile char[] secret;

        public static void set(char[] pukChars) {
            char[] old = secret;
            if (old != null) Arrays.fill(old, '\0');
            secret = pukChars.clone();
        }

        /** Sobrecarga String para compatibilidad con llamadores existentes. */
        public static void set(String puk) {
            set(puk.toCharArray());
        }

        public static char[] getAndClear() {
            char[] s = secret;
            secret = null;
            return s;
        }
    }
}