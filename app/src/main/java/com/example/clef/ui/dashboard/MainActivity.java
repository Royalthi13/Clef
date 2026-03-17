package com.example.clef.ui.dashboard;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.clef.R;
import com.example.clef.ui.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private VaultFragment vaultFragment;
    private SettingsFragment settingsFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            vaultFragment = new VaultFragment();
            settingsFragment = new SettingsFragment();
            fm.beginTransaction()
                    .add(R.id.fragmentContainer, settingsFragment, "settings").hide(settingsFragment)
                    .add(R.id.fragmentContainer, vaultFragment, "vault")
                    .commit();
            activeFragment = vaultFragment;
        } else {
            // Recuperar fragments existentes tras recreación (ej. cambio de tema)
            vaultFragment = (VaultFragment) fm.findFragmentByTag("vault");
            settingsFragment = (SettingsFragment) fm.findFragmentByTag("settings");
            activeFragment = settingsFragment.isHidden() ? vaultFragment : settingsFragment;
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(activeFragment == settingsFragment ? R.id.nav_settings : R.id.nav_vault);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_vault) {
                switchTo(vaultFragment);
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
        if (target == activeFragment) return;
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }
}
