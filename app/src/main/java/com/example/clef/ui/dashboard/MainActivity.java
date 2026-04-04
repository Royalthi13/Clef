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

        long savedMs = getSharedPreferences("settings", 0).getLong("auto_lock_ms", 300_000);
        SessionManager.getInstance().setLockTimeout(savedMs);

        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            // Primera vez: crear todos los fragments y añadirlos al contenedor
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
            // Recreación (cambio de tema, rotación, sistema mata el proceso, etc.)
            // Los fragments ya están en el back-stack del FragmentManager.
            vaultFragment     = (VaultFragment)    fm.findFragmentByTag("vault");
            generatorFragment =                    fm.findFragmentByTag("generator");
            settingsFragment  = (SettingsFragment) fm.findFragmentByTag("settings");

            // BUG ANTERIOR: si algún fragment era null (proceso matado por el sistema),
            // se creaba uno nuevo con `new` pero NO se añadía al FragmentManager.
            // La siguiente llamada a switchTo() intentaba hide(activeFragment) con un
            // fragment sin adjuntar → IllegalStateException.
            //
            // SOLUCIÓN: si faltan fragments, recrear la pila completa desde cero
            // en lugar de mezclar fragments existentes con nuevos sin adjuntar.
            if (vaultFragment == null || generatorFragment == null || settingsFragment == null) {
                // El estado del FragmentManager es inconsistente — reconstruir todo
                vaultFragment     = vaultFragment     != null ? vaultFragment     : new VaultFragment();
                generatorFragment = generatorFragment != null ? generatorFragment : new GeneratorFragment();
                settingsFragment  = settingsFragment  != null ? settingsFragment  : new SettingsFragment();

                // Limpiar el back-stack actual para evitar duplicados
                fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

                fm.beginTransaction()
                        .replace(R.id.fragmentContainer, settingsFragment,  "settings")
                        .hide(settingsFragment)
                        .commit();
                fm.beginTransaction()
                        .add(R.id.fragmentContainer, generatorFragment, "generator")
                        .hide(generatorFragment)
                        .commit();
                fm.beginTransaction()
                        .add(R.id.fragmentContainer, vaultFragment, "vault")
                        .commit();

                activeFragment = vaultFragment;
            } else {
                // Todos los fragments están en el FragmentManager: determinar cuál era activo
                if (!vaultFragment.isHidden()) {
                    activeFragment = vaultFragment;
                } else if (!generatorFragment.isHidden()) {
                    activeFragment = generatorFragment;
                } else if (!settingsFragment.isHidden()) {
                    activeFragment = settingsFragment;
                } else {
                    // Estado incoherente (ninguno visible) → mostrar vault como fallback
                    activeFragment = vaultFragment;
                    fm.beginTransaction().show(vaultFragment).commit();
                }
            }

            // Sincronizar el BottomNav con el fragment activo
            syncBottomNav();
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
    protected void onStart() {
        super.onStart();
        SessionManager.getInstance().cancelLockTimer();
        if (!SessionManager.getInstance().isUnlocked()) {
            startActivity(new android.content.Intent(this, UnlockActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("session_expired", true));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        SessionManager.getInstance().startLockTimer();
    }

    @Override
    public void recreate() {
        super.recreate();
        overridePendingTransition(0, 0);
    }

    private void switchTo(Fragment target) {
        if (target == null || target == activeFragment) return;
        if (activeFragment == null) { activeFragment = target; return; }

        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }

    /**
     * Sincroniza el ítem seleccionado en el BottomNavigationView con el fragment activo.
     * Solo necesario tras la recreación de la Activity.
     */
    private void syncBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav == null || activeFragment == null) return;
        if (activeFragment == settingsFragment) {
            bottomNav.setSelectedItemId(R.id.nav_settings);
        } else if (activeFragment == generatorFragment) {
            bottomNav.setSelectedItemId(R.id.nav_generator);
        } else {
            bottomNav.setSelectedItemId(R.id.nav_vault);
        }
    }
}