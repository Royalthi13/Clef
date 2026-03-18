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
    private Fragment generatorFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            // Es la primera vez que se abre la app, creamos los 3 fragmentos
            vaultFragment = new VaultFragment();
            settingsFragment = new SettingsFragment();
            generatorFragment = new GeneratorFragment();

            // Los añadimos todos al contenedor, pero ocultamos los de Ajustes y Generador
            fm.beginTransaction()
                    .add(R.id.fragmentContainer, settingsFragment, "settings").hide(settingsFragment)
                    .add(R.id.fragmentContainer, generatorFragment, "generator").hide(generatorFragment)
                    .add(R.id.fragmentContainer, vaultFragment, "vault")
                    .commit();
            // La Bóveda es la que se queda visible por defecto
            activeFragment = vaultFragment;
        } else {
            // Recuperar fragments existentes tras recreación (ej. cambio de tema)
            vaultFragment = (VaultFragment) fm.findFragmentByTag("vault");
            generatorFragment = (GeneratorFragment) fm.findFragmentByTag("generator");
            settingsFragment = (SettingsFragment) fm.findFragmentByTag("settings");

            // Averiguamos cuál estaba activo antes de girar la pantalla
            if (vaultFragment != null && !vaultFragment.isHidden()) activeFragment = vaultFragment;
            else if (settingsFragment != null && !settingsFragment.isHidden()) activeFragment = settingsFragment;
            else if (generatorFragment != null && !generatorFragment.isHidden()) activeFragment = generatorFragment;
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        // Escuchamos qué botón toca el usuario en la barra inferior
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

    // Este es el truco de magia: oculta el actual y muestra el nuevo
    private void switchTo(Fragment target) {
        if (target == null || target == activeFragment) return;
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }
}
