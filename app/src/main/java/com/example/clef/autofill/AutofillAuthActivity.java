package com.example.clef.autofill;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.service.autofill.Dataset;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.clef.R;
import com.example.clef.crypto.KeyManager;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
import com.example.clef.data.remote.FirebaseManager;
import com.example.clef.data.repository.VaultRepository;
import com.example.clef.utils.BiometricHelper;
import com.example.clef.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Activity lanzada por el AutofillService al pulsar "Desbloquear Clef".
 *
 * Flujo único (simplificado):
 *   1. Biometría SIEMPRE (no reutilizamos la sesión de Clef para no bajar el
 *      listón de seguridad — cualquier app puede disparar autofill).
 *   2. Descifrar vault.
 *   3. Mostrar un picker filtrado con las credenciales relevantes para la app
 *      que ha pedido autofill.
 *   4. Al elegir una credencial: rellenar campos y aprender packageHint.
 *
 * Nunca revela credenciales sin biometría. La DEK se zeriza al salir.
 */
public class AutofillAuthActivity extends AppCompatActivity {

    public static final String EXTRA_USERNAME_ID  = "username_id";
    public static final String EXTRA_PASSWORD_ID  = "password_id";
    public static final String EXTRA_FOCUSED_ID   = "focused_id";
    public static final String EXTRA_PACKAGE_NAME = "package_name";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AutofillId userId;
    private AutofillId passId;
    private AutofillId focusedId;
    private String     pkg;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userId    = getIntent().getParcelableExtra(EXTRA_USERNAME_ID);
        passId    = getIntent().getParcelableExtra(EXTRA_PASSWORD_ID);
        focusedId = getIntent().getParcelableExtra(EXTRA_FOCUSED_ID);
        pkg       = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);

        if (userId == null && passId == null) {
            finishWithFailure();
            return;
        }

        if (!BiometricHelper.isAvailable(this) || !BiometricHelper.isEnabled(this)) {
            Toast.makeText(this,
                    "Activa la biometría en Clef para usar autofill",
                    Toast.LENGTH_LONG).show();
            finishWithFailure();
            return;
        }

        BiometricHelper.unlock(this, new BiometricHelper.UnlockCallback() {
            @Override
            public void onSuccess(byte[] dek) {
                executor.execute(() -> {
                    Vault v = null;
                    try {
                        VaultRepository repo = new VaultRepository(AutofillAuthActivity.this);
                        FirebaseManager.UserData data = repo.loadOfflineUserData();
                        if (data == null || data.vault == null) {
                            runOnUiThread(AutofillAuthActivity.this::finishWithFailure);
                            return;
                        }
                        v = new KeyManager().descifrarVault(data.vault, dek);
                    } catch (Exception e) {
                        runOnUiThread(AutofillAuthActivity.this::finishWithFailure);
                        return;
                    } finally {
                        // Zerisamos la DEK lo antes posible — ya hemos descifrado.
                        SessionManager.zeroizeDekCopy(dek);
                    }
                    final Vault vault = v;
                    runOnUiThread(() -> showPicker(vault));
                });
            }

            @Override public void onError(String message) { finishWithFailure(); }
            @Override public void onCancelled()           { finishWithFailure(); }
        });
    }

    // ── Picker ───────────────────────────────────────────────────────────────

    private void showPicker(Vault vault) {
        if (vault == null || vault.getCredentials() == null
                || vault.getCredentials().isEmpty()) {
            Toast.makeText(this, "Tu bóveda está vacía", Toast.LENGTH_SHORT).show();
            finishWithFailure();
            return;
        }

        setContentView(R.layout.activity_autofill_picker);

        // Header dinámico: app objetivo
        TextView tvTitle = findViewById(R.id.tvPickerHeaderTitle);
        TextView tvSubtitle = findViewById(R.id.tvPickerHeaderSubtitle);
        tvTitle.setText("Elige una credencial");

        String app = appLabelOrPkg(pkg);
        tvSubtitle.setText("Para " + (app != null ? app : "esta app"));

        findViewById(R.id.btnPickerClose)
                .setOnClickListener(v -> finishWithFailure());

        // Ranking con filtro estricto: solo matches, y si no hay → toda la bóveda
        List<RankedCredential> ranked = rankForUi(vault.getCredentials(), pkg);

        TextView tvEmpty = findViewById(R.id.tvPickerEmpty);
        RecyclerView rv = findViewById(R.id.rvPicker);
        rv.setLayoutManager(new LinearLayoutManager(this));

        boolean anyMatches = !ranked.isEmpty() && ranked.get(0).score > 0;
        if (anyMatches) {
            tvEmpty.setVisibility(View.GONE);
        } else {
            // No hay matches — mostramos todas pero con aviso suave.
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("Ninguna credencial coincide con esta app.\nMuestra toda la bóveda:");
        }

        rv.setAdapter(new PickerAdapter(ranked, this::returnSingleDataset));
    }

    // ── Devolver dataset ─────────────────────────────────────────────────────

    private void returnSingleDataset(Credential c) {
        // Después de auth, Android rellena los campos con el valor del dataset.
        // La presentation aquí NO se muestra (ya hemos pasado la auth) pero
        // el Builder la necesita. Usamos una del sistema que siempre compila.
        RemoteViews notUsed = new RemoteViews(
                getPackageName(), android.R.layout.simple_list_item_1);
        notUsed.setTextViewText(android.R.id.text1,
                c.getTitle() != null ? c.getTitle() : "Clef");

        Dataset.Builder b = new Dataset.Builder(notUsed);

        String user = safe(c.getUsername());
        String pass = safe(c.getPassword());

        if (userId != null) {
            b.setValue(userId, AutofillValue.forText(user));
        }
        if (passId != null) {
            b.setValue(passId, AutofillValue.forText(pass));
        }
        // Si el campo enfocado no es ni username ni password, lo rellenamos
        // con el username (para que Android lo acepte y muestre el autofill).
        if (focusedId != null
                && !focusedId.equals(userId)
                && !focusedId.equals(passId)) {
            b.setValue(focusedId, AutofillValue.forText(user));
        }

        // Aprender packageHint para próximas veces — fire & forget
        rememberPackageHintAsync(c, pkg);

        // Guardar en sesión transitoria para evitar pedir biometría otra vez
        // en la siguiente pantalla del flujo (Amazon/OpenAI piden email en
        // una pantalla y password en otra).
        AutofillTransientSession.store(c);

        Intent reply = new Intent();
        reply.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, b.build());
        setResult(Activity.RESULT_OK, reply);
        finish();
    }

    /**
     * Re-cifra el vault en background añadiendo el packageHint actual a la
     * credencial seleccionada. Silencioso en caso de fallo: no bloquea al usuario.
     */
    private void rememberPackageHintAsync(Credential c, String pkgName) {
        if (pkgName == null || pkgName.isEmpty()) return;
        if (c.getPackageHints() != null && c.getPackageHints().contains(pkgName)) return;

        // Necesitamos un DEK para re-cifrar. Solo disponible si la app de Clef
        // está abierta en primer plano con sesión viva; en ese caso escribimos.
        // Si no, no pasa nada: se aprenderá la próxima vez que el usuario esté
        // dentro de Clef y vuelva a usar autofill.
        final byte[] dek = SessionManager.getInstance().getDek();
        if (dek == null) return;

        final Vault vault = SessionManager.getInstance().getVault();
        if (vault == null) { SessionManager.zeroizeDekCopy(dek); return; }

        executor.execute(() -> {
            try {
                // Buscar la credencial por título+username (misma instancia no
                // está garantizada si SessionManager ha actualizado el vault).
                Credential target = findMatching(vault, c);
                if (target == null) return;

                target.addPackageHint(pkgName);
                target.setUpdatedAt(System.currentTimeMillis());
                target.setLastUsedAt(System.currentTimeMillis());

                KeyManager km = new KeyManager();
                String enc = km.cifrarVault(vault, dek);
                new VaultRepository(AutofillAuthActivity.this).saveLocalVaultOnly(enc);
                SessionManager.getInstance().updateVault(vault);
            } catch (Exception ignored) {
                // no-op
            } finally {
                SessionManager.zeroizeDekCopy(dek);
            }
        });
    }

    private static Credential findMatching(Vault v, Credential target) {
        if (v.getCredentials() == null) return null;
        String t = safe(target.getTitle());
        String u = safe(target.getUsername());
        for (Credential c : v.getCredentials()) {
            if (safe(c.getTitle()).equals(t) && safe(c.getUsername()).equals(u)) return c;
        }
        return null;
    }

    // ── Ranking ──────────────────────────────────────────────────────────────

    private static class RankedCredential {
        final Credential credential;
        final int score;
        RankedCredential(Credential c, int score) {
            this.credential = c; this.score = score;
        }
    }

    /**
     * Ordena las credenciales por relevancia. Si hay al menos un match (score>0),
     * devuelve solo esos. Si no hay ninguno, devuelve todas para que el usuario
     * pueda elegir manualmente (evita la trampa de lista vacía).
     */
    private static List<RankedCredential> rankForUi(List<Credential> creds, String pkg) {
        List<RankedCredential> all = new ArrayList<>();
        int best = 0;
        for (Credential c : creds) {
            int s = scoreFor(c, pkg);
            all.add(new RankedCredential(c, s));
            if (s > best) best = s;
        }
        all.sort((a, b) -> Integer.compare(b.score, a.score));

        if (best > 0) {
            // Devolver solo los que matchean
            List<RankedCredential> filtered = new ArrayList<>();
            for (RankedCredential r : all) {
                if (r.score > 0) filtered.add(r);
            }
            return filtered;
        }
        return all; // sin matches → todas
    }

    private static int scoreFor(Credential c, String pkg) {
        if (pkg == null) return 0;
        int score = 0;
        String pkgLower = pkg.toLowerCase();

        // 1. Match exacto por packageHint aprendido (mayor peso)
        if (c.getPackageHints() != null && c.getPackageHints().contains(pkg)) {
            score += 1000;
        }

        // 2. Match por URL/dominio
        if (c.getUrl() != null && !c.getUrl().isEmpty()) {
            String domain = c.getUrl().toLowerCase()
                    .replaceAll("^https?://", "")
                    .replaceAll("^www\\.", "")
                    .replaceAll("/.*$", "")
                    .replaceAll(":.*$", "");
            if (!domain.isEmpty()) {
                // "linkedin.com" → "linkedin"
                String base = domain.contains(".")
                        ? domain.substring(0, domain.indexOf('.'))
                        : domain;
                if (base.length() >= 3 && pkgLower.contains(base)) {
                    score += 400;
                }
            }
        }

        // 3. Match por título
        if (c.getTitle() != null) {
            String t = c.getTitle().toLowerCase().replaceAll("\\s+", "");
            if (t.length() >= 3 && pkgLower.contains(t)) {
                score += 200;
            }
        }

        return score;
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    private String appLabelOrPkg(String pkgName) {
        if (pkgName == null) return null;
        try {
            android.content.pm.ApplicationInfo ai =
                    getPackageManager().getApplicationInfo(pkgName, 0);
            CharSequence label = getPackageManager().getApplicationLabel(ai);
            return label != null ? label.toString() : pkgName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return pkgName;
        }
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

    // ── Adapter ──────────────────────────────────────────────────────────────

    private interface OnPick { void pick(Credential c); }

    private static class PickerAdapter extends RecyclerView.Adapter<PickerAdapter.VH> {
        private final List<RankedCredential> items;
        private final OnPick listener;

        PickerAdapter(List<RankedCredential> items, OnPick listener) {
            this.items = items; this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_autofill_picker, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            RankedCredential r = items.get(position);
            Credential c = r.credential;

            h.title.setText(c.getTitle() != null && !c.getTitle().isEmpty()
                    ? c.getTitle() : "(sin título)");
            h.user.setText(c.getUsername() != null ? c.getUsername() : "");

            // Primera letra del título como "avatar" — truco visual barato pero efectivo
            String letter = "?";
            if (c.getTitle() != null && !c.getTitle().isEmpty()) {
                letter = c.getTitle().substring(0, 1).toUpperCase();
            }
            h.avatar.setText(letter);

            // Badge "Sugerido" si score alto
            if (r.score >= 400) {
                h.badge.setVisibility(View.VISIBLE);
            } else {
                h.badge.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> listener.pick(c));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            final TextView title, user, avatar, badge;
            VH(View v) {
                super(v);
                title  = v.findViewById(R.id.tvPickerTitle);
                user   = v.findViewById(R.id.tvPickerUsername);
                avatar = v.findViewById(R.id.tvPickerAvatar);
                badge  = v.findViewById(R.id.tvPickerBadge);
            }
        }
    }
}
