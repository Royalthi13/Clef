package com.example.clef.ui.dashboard;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.clef.R;
import com.example.clef.ui.auth.UnlockActivity;
import com.example.clef.ui.settings.SettingsFragment;
import com.example.clef.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private VaultFragment    vaultFragment;
    private SettingsFragment settingsFragment;
    private Fragment         generatorFragment;
    private Fragment         activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SessionManager.getInstance().setOnLockListener(() -> {
            startActivity(new android.content.Intent(this, UnlockActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("session_expired", true));
        });

        long savedMs = getSharedPreferences("settings", 0)
                .getLong("auto_lock_ms", 60_000);
        SessionManager.getInstance().setLockTimeout(savedMs);
        SessionManager.getInstance().resetTimer();

        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            vaultFragment     = new VaultFragment();
            settingsFragment  = new SettingsFragment();
            generatorFragment = new GeneratorFragment();

            fm.beginTransaction()
                    .add(R.id.fragmentContainer, settingsFragment,  "settings") .hide(settingsFragment)
                    .add(R.id.fragmentContainer, generatorFragment, "generator").hide(generatorFragment)
                    .add(R.id.fragmentContainer, vaultFragment,     "vault")
                    .commit();

            activeFragment = vaultFragment;
        } else {
            // FIX: null-checks en todos los fragments — pueden ser null si el back-stack
            // estaba en un estado inesperado (cambio de tema, recreación por sistema).
            vaultFragment     = (VaultFragment)    fm.findFragmentByTag("vault");
            generatorFragment =                    fm.findFragmentByTag("generator");
            settingsFragment  = (SettingsFragment) fm.findFragmentByTag("settings");

            // Si algún fragment es null por algún motivo, recrearlo
            if (vaultFragment == null)     vaultFragment     = new VaultFragment();
            if (generatorFragment == null) generatorFragment = new GeneratorFragment();
            if (settingsFragment == null)  settingsFragment  = new SettingsFragment();

            // Determinar cuál era el activo
            if (!vaultFragment.isAdded() || !vaultFragment.isHidden()) {
                activeFragment = vaultFragment;
            } else if (generatorFragment.isAdded() && !generatorFragment.isHidden()) {
                activeFragment = generatorFragment;
            } else if (settingsFragment.isAdded() && !settingsFragment.isHidden()) {
                activeFragment = settingsFragment;
            } else {
                activeFragment = vaultFragment; // fallback seguro
            }
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_vault) {
                switchTo(vaultFragment);
                return true;
            } else if (id == R.id.nav_generator) {
                switchTo(generatorFragment);
                return true;
            } else if (id == R.id.nav_settings) {
                switchTo(settingsFragment);
                return true;
            }
            return false;
        });
    }

    @Override
    public void recreate() {
        super.recreate();
        overridePendingTransition(0, 0);
    }

    private void switchTo(Fragment target) {
        if (target == null || target == activeFragment) return;
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }
}