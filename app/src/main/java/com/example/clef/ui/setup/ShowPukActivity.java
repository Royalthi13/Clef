package com.example.clef.ui.setup;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.R;
import com.example.clef.ui.dashboard.MainActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class ShowPukActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_puk);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        String puk = getIntent().getStringExtra(CreateMasterActivity.EXTRA_PUK);
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
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("PUK", puk));
                Toast.makeText(this, R.string.puk_copied, Toast.LENGTH_SHORT).show();
            }
        });

        btnContinue.setOnClickListener(v -> goToMain());
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }
}
