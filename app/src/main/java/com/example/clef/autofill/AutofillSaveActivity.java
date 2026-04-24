package com.example.clef.autofill;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.CategoryDetector;
import com.example.clef.utils.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity que se lanza cuando el AutofillService recibe un onSaveRequest.
 *
 * Flujo:
 *   1. Biometría (siempre, por seguridad).
 *   2. Descifrar vault.
 *   3. Si ya existe una credencial para (pkg, username) → SOLO actualizar
 *      password + packageHint + lastUsedAt. No duplica.
 *   4. Si no existe → crear nueva credencial con CategoryDetector.
 *   5. Re-cifrar y guardar localmente.
 *
 * No pregunta "¿quieres guardar?" — Android ya lo hizo con su save dialog.
 */
public class AutofillSaveActivity extends AppCompatActivity {

    public static final String EXTRA_USERNAME     = "username";
    public static final String EXTRA_PASSWORD     = "password";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    public static final String EXTRA_APP_LABEL    = "app_label";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String user    = getIntent().getStringExtra(EXTRA_USERNAME);
        String pass    = getIntent().getStringExtra(EXTRA_PASSWORD);
        String pkg     = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        String appName = getIntent().getStringExtra(EXTRA_APP_LABEL);

        if (pass == null || pass.isEmpty()) {
            finishWithFailure();
            return;
        }

        if (!BiometricHelper.isAvailable(this) || !BiometricHelper.isEnabled(this)) {
            Toast.makeText(this,
                    "Activa la biometría en Clef para guardar credenciales desde autofill",
                    Toast.LENGTH_LONG).show();
            finishWithFailure();
            return;
        }

        BiometricHelper.unlock(this, new BiometricHelper.UnlockCallback() {
            @Override
            public void onSuccess(byte[] dek) {
                executor.execute(() -> saveInBackground(dek, user, pass, pkg, appName));
            }
            @Override public void onError(String message) { finishWithFailure(); }
            @Override public void onCancelled()           { finishWithFailure(); }
        });
    }

    private void saveInBackground(byte[] dek, String user, String pass,
                                  String pkg, String appName) {
        try {
            VaultRepository repo = new VaultRepository(this);
            FirebaseManager.UserData data = repo.loadOfflineUserData();
            if (data == null || data.vault == null) { mainFail("No hay bóveda local"); return; }

            KeyManager km = new KeyManager();
            Vault vault = km.descifrarVault(data.vault, dek);

            String safeUser = safe(user);
            String label = appName != null && !appName.isEmpty() ? appName : pkg;

            // ¿Ya existe una credencial para este pkg+username?
            Credential existing = findExisting(vault, pkg, safeUser);
            final String feedback;

            if (existing != null) {
                // Update silencioso: nueva password, hint confirmado, lastUsedAt.
                // Guardamos la anterior en historial (aprovechamos el API de la app).
                if (existing.getPassword() != null
                        && !existing.getPassword().equals(pass)) {
                    existing.addToHistory(existing.getPassword());
                }
                existing.setPassword(pass);
                existing.addPackageHint(pkg);
                existing.setUpdatedAt(System.currentTimeMillis());
                existing.setLastUsedAt(System.currentTimeMillis());
                feedback = "Credencial de " + label + " actualizada";
            } else {
                Credential c = new Credential(
                        label, safeUser, pass, "", "",
                        firstNonNull(CategoryDetector.detect(label, null),
                                Credential.Category.OTHER));
                c.setUpdatedAt(System.currentTimeMillis());
                c.setLastUsedAt(System.currentTimeMillis());
                c.addPackageHint(pkg);
                vault.addCredential(c);
                feedback = "Guardado en Clef: " + label;
            }

            String encrypted = km.cifrarVault(vault, dek);
            repo.saveLocalVaultOnly(encrypted);

            // Reflejar en sesión viva si la app está abierta
            SessionManager session = SessionManager.getInstance();
            if (session.isUnlocked()) session.updateVault(vault);

            // Invalidar cualquier sesión transitoria — la credencial ha cambiado
            AutofillTransientSession.clear();

            mainHandler.post(() -> {
                Toast.makeText(this, feedback, Toast.LENGTH_SHORT).show();
                setResult(Activity.RESULT_OK);
                finish();
            });
        } catch (Exception e) {
            mainFail("Error al guardar");
        } finally {
            SessionManager.zeroizeDekCopy(dek);
        }
    }

    /**
     * Busca una credencial existente por (packageHint + username) o por
     * (título que parece ser la misma app + username). Evita duplicados.
     */
    private static Credential findExisting(Vault vault, String pkg, String username) {
        if (vault.getCredentials() == null) return null;
        String pkgLower = pkg != null ? pkg.toLowerCase() : null;

        for (Credential c : vault.getCredentials()) {
            String u = c.getUsername() != null ? c.getUsername() : "";
            if (!u.equalsIgnoreCase(username)) continue;

            // Match por packageHint exacto
            if (pkg != null && c.getPackageHints() != null
                    && c.getPackageHints().contains(pkg)) {
                return c;
            }

            // Match por título/dominio contenido en el paquete
            if (pkgLower != null && c.getTitle() != null) {
                String t = c.getTitle().toLowerCase().replaceAll("\\s+", "");
                if (t.length() >= 3 && pkgLower.contains(t)) return c;
            }
        }
        return null;
    }

    private void mainFail(String message) {
        mainHandler.post(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
    }

    private void finishWithFailure() {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static <T> T firstNonNull(T a, T b) { return a != null ? a : b; }
}
