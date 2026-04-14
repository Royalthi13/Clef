package com.example.clef.ui.dashboard;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.clef.R;
import com.example.clef.ui.auth.UnlockActivity;
import com.example.clef.ui.settings.SettingsFragment;
import com.example.clef.utils.SecurePrefs;
import com.example.clef.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * B-8 FIX: onResume() llamaba a startLockTimer() que programa un bloqueo
 * en background aunque la app esté en primer plano. Esto causaba bloqueos
 * prematuros si el usuario no interactuaba durante lockTimeoutMs.
 *
 * El timer de bloqueo SOLO debe iniciarse cuando la app va a background (onStop).
 * En onResume solo hay que cancelarlo (app vuelve a primer plano).
 */
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

        requestNotificationPermission();

        long savedMs = SecurePrefs.get(this, "settings").getLong("auto_lock_ms", 300_000);
        SessionManager.getInstance().setLockTimeout(savedMs);

        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            vaultFragment     = new VaultFragment();
            settingsFragment  = new SettingsFragment();
            generatorFragment = new GeneratorFragment();

            fm.beginTransaction()
                    .add(R.id.fragmentContainer, settingsFragment,  "settings").hide(settingsFragment)
                    .add(R.id.fragmentContainer, generatorFragment, "generator").hide(generatorFragment)
                    .add(R.id.fragmentContainer, vaultFragment,     "vault")
                    .commit();
            activeFragment = vaultFragment;
        } else {
            vaultFragment     = (VaultFragment)    fm.findFragmentByTag("vault");
            generatorFragment =                    fm.findFragmentByTag("generator");
            settingsFragment  = (SettingsFragment) fm.findFragmentByTag("settings");

            if (vaultFragment == null || generatorFragment == null || settingsFragment == null) {
                vaultFragment     = vaultFragment     != null ? vaultFragment     : new VaultFragment();
                generatorFragment = generatorFragment != null ? generatorFragment : new GeneratorFragment();
                settingsFragment  = settingsFragment  != null ? settingsFragment  : new SettingsFragment();

                fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                fm.beginTransaction()
                        .replace(R.id.fragmentContainer, settingsFragment, "settings")
                        .hide(settingsFragment).commit();
                fm.beginTransaction()
                        .add(R.id.fragmentContainer, generatorFragment, "generator")
                        .hide(generatorFragment).commit();
                fm.beginTransaction()
                        .add(R.id.fragmentContainer, vaultFragment, "vault").commit();
                activeFragment = vaultFragment;
            } else {
                if      (!vaultFragment.isHidden())     activeFragment = vaultFragment;
                else if (!generatorFragment.isHidden()) activeFragment = generatorFragment;
                else if (!settingsFragment.isHidden())  activeFragment = settingsFragment;
                else {
                    activeFragment = vaultFragment;
                    fm.beginTransaction().show(vaultFragment).commit();
                }
            }
            syncBottomNav();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_vault)     { switchTo(vaultFragment);     return true; }
            else if (id == R.id.nav_generator) { switchTo(generatorFragment); return true; }
            else if (id == R.id.nav_settings)  { switchTo(settingsFragment);  return true; }
            return false;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // B-8 FIX: solo cancelar el timer (app vuelve a foreground).
        SessionManager.getInstance().cancelLockTimer();
        if (!SessionManager.getInstance().isUnlocked()) {
            startActivity(new android.content.Intent(this, UnlockActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("session_expired", true));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // B-8 FIX: NO llamar a startLockTimer() aquí.
        // El timer se inicia en onStop() cuando la app realmente va a background.
        // Llamarlo en onResume() causaba bloqueos prematuros.
    }

    @Override
    protected void onStop() {
        super.onStop();
        // B-8 FIX: iniciar el timer solo cuando la app va a background.
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
                .hide(activeFragment).show(target).commit();
        activeFragment = target;
    }

    private void syncBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav == null || activeFragment == null) return;
        if      (activeFragment == settingsFragment)  bottomNav.setSelectedItemId(R.id.nav_settings);
        else if (activeFragment == generatorFragment) bottomNav.setSelectedItemId(R.id.nav_generator);
        else                                          bottomNav.setSelectedItemId(R.id.nav_vault);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }
}