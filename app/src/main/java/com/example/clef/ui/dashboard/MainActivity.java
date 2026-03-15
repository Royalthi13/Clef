package com.example.clef.ui.dashboard;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.clef.R;
import com.example.clef.ui.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private final VaultFragment vaultFragment = new VaultFragment();
    private final SettingsFragment settingsFragment = new SettingsFragment();
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fm = getSupportFragmentManager();

        // Registrar los dos fragments al inicio (evita recrearlos al cambiar de pestaña)
        fm.beginTransaction()
                .add(R.id.fragmentContainer, settingsFragment, "settings").hide(settingsFragment)
                .add(R.id.fragmentContainer, vaultFragment, "vault")
                .commit();
        activeFragment = vaultFragment;

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
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

    private void switchTo(Fragment target) {
        if (target == activeFragment) return;
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }
}
