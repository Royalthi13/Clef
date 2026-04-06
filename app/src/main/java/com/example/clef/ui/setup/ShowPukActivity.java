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

public class ShowPukActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_puk);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String puk = TempSecretHolder.getAndClear();
        if (puk == null || puk.isEmpty()) {
            Toast.makeText(this, R.string.puk_missing, Toast.LENGTH_LONG).show();
            goToMain();
            return;
        }

        TextView tvPuk = findViewById(R.id.tvPukCode);
        tvPuk.setText(puk);

        MaterialButton btnCopy = findViewById(R.id.btnCopyPuk);
        MaterialButton btnContinue = findViewById(R.id.btnContinue);

        btnCopy.setOnClickListener(v -> {
            ClipboardHelper.copySensitivePuk(this, "PUK", puk);
            Toast.makeText(this, R.string.puk_copied, Toast.LENGTH_SHORT).show();
        });

        btnContinue.setOnClickListener(v -> goToMain());
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
    public static class TempSecretHolder {
        private static String secret;
        public static void set(String s) { secret = s; }
        public static String getAndClear() {
            String s = secret;
            secret = null;
            return s;
        }
    }
}
