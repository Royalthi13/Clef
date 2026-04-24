package com.example.clef.autofill;

import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.InlinePresentation;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveInfo;
import android.service.autofill.SaveRequest;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.autofill.inline.v1.InlineSuggestionUi;

import com.example.clef.R;
import com.example.clef.data.model.Credential;
import com.example.clef.data.model.Vault;
import com.example.clef.utils.SessionManager;

import java.util.List;

/**
 * Servicio de autofill de Clef (minSdk=28, targetSdk=36).
 *
 * Estrategia:
 *   1. Un ÚNICO dataset "Desbloquear Clef" que dispara biometría y abre el
 *      picker interno con filtrado por app. Evita exponer nada sin auth.
 *   2. Si el IME soporta inline suggestions (Android 11+), el mismo dataset
 *      se presenta además como pastilla en el teclado — mismo flujo de auth.
 *   3. Header con branding "Clef • <app>" en el dropdown.
 *   4. SaveInfo permite a Android ofrecer "Guardar en Clef" al rellenar.
 *
 * Mitiga P1/P3 del paper ACM de autofill: no revelamos valores sin biometría,
 * y el picker filtrado evita phishing por apps con packageName similar.
 */
@RequiresApi(Build.VERSION_CODES.O)
public class ClefAutofillService extends AutofillService {

    private static final String TAG = "ClefAutofillService";

    @Override
    public void onFillRequest(@NonNull FillRequest request,
                              @NonNull CancellationSignal cancellationSignal,
                              @NonNull FillCallback callback) {
        List<FillContext> contexts = request.getFillContexts();
        if (contexts.isEmpty()) { callback.onSuccess(null); return; }

        AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();
        String callingPackage = getCallingPackage(structure);

        // Seguridad: nunca rellenar en nosotros mismos.
        if (getPackageName().equals(callingPackage)) {
            callback.onSuccess(null);
            return;
        }

        FieldFinder.Fields fields = FieldFinder.find(structure);
        android.util.Log.d(TAG, "onFillRequest: pkg=" + callingPackage
                + " u=" + fields.usernameId
                + " p=" + fields.passwordId
                + " focused=" + fields.focusedId);

        if (!fields.hasAny()) {
            callback.onSuccess(null);
            return;
        }

        FillResponse.Builder respBuilder = new FillResponse.Builder();

        // ¿El IME pide inline? (Android 11+)
        InlinePresentationSpec firstInlineSpec = getFirstInlineSpec(request);

        // ¿Hay una credencial vigente de una biometría reciente (≤30s)?
        // Flujo típico: Amazon/OpenAI piden email primero y password después
        // en pantallas distintas. No queremos pedir biometría dos veces.
        Credential recent = AutofillTransientSession.get();
        if (recent != null) {
            Dataset direct = buildDirectDataset(recent, fields, firstInlineSpec);
            if (direct != null) respBuilder.addDataset(direct);
        } else {
            Dataset unlock = buildUnlockDataset(fields, callingPackage, firstInlineSpec);
            if (unlock != null) respBuilder.addDataset(unlock);
        }

        // SaveInfo
        if (fields.hasPassword()) {
            AutofillId[] required;
            int saveFlags = SaveInfo.SAVE_DATA_TYPE_PASSWORD;
            if (fields.hasUsername()) {
                required = new AutofillId[]{ fields.usernameId, fields.passwordId };
                saveFlags |= SaveInfo.SAVE_DATA_TYPE_USERNAME;
            } else {
                required = new AutofillId[]{ fields.passwordId };
            }
            respBuilder.setSaveInfo(new SaveInfo.Builder(saveFlags, required).build());
        }

        // Header con branding — solo en dropdown, no afecta inline.
        RemoteViews header = new RemoteViews(getPackageName(), R.layout.autofill_header);
        header.setTextViewText(R.id.autofillHeaderTitle, labelForHeader(callingPackage));
        respBuilder.setHeader(header);

        callback.onSuccess(respBuilder.build());
    }

    /**
     * Construye el dataset "Desbloquear Clef". Incluye presentation de dropdown
     * y, si aplica, inline presentation para teclados compatibles.
     *
     * En todas las APIs soportadas (28+) el constructor Dataset.Builder(RemoteViews)
     * está disponible. En API 30+ setValue(id, null, inlinePresentation) añade
     * la versión inline al mismo dataset.
     */
    private Dataset buildUnlockDataset(FieldFinder.Fields fields,
                                       String callingPackage,
                                       InlinePresentationSpec inlineSpec) {
        // Presentation de dropdown (siempre)
        RemoteViews dropdown = new RemoteViews(getPackageName(), R.layout.autofill_item);
        dropdown.setTextViewText(R.id.autofillTitle,    "Desbloquear Clef");
        dropdown.setTextViewText(R.id.autofillSubtitle, "Toca para elegir una credencial");

        Intent auth = new Intent(this, AutofillAuthActivity.class);
        auth.putExtra(AutofillAuthActivity.EXTRA_USERNAME_ID, fields.usernameId);
        auth.putExtra(AutofillAuthActivity.EXTRA_PASSWORD_ID, fields.passwordId);
        auth.putExtra(AutofillAuthActivity.EXTRA_FOCUSED_ID,  fields.focusedId);
        auth.putExtra(AutofillAuthActivity.EXTRA_PACKAGE_NAME, callingPackage);

        PendingIntent pi = PendingIntent.getActivity(
                this, 1, auth,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);

        // Constructor estable para todas las APIs
        Dataset.Builder b = new Dataset.Builder(dropdown);

        // Inline suggestion (Android 11+)
        InlinePresentation inlinePresentation = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineSpec != null) {
            inlinePresentation = buildInlinePresentation(
                    "Desbloquear Clef",
                    "Toca para elegir",
                    pi,
                    inlineSpec);
        }

        // Añadir campos al dataset — placeholders null, los valores reales
        // los pone AutofillAuthActivity tras biometría.
        addField(b, fields.usernameId, dropdown, inlinePresentation);
        addField(b, fields.passwordId, dropdown, inlinePresentation);

        // Si el campo enfocado no era ni username ni password, añadirlo igual
        // para que Android muestre el popup al tocarlo.
        if (fields.focusedId != null
                && !fields.focusedId.equals(fields.usernameId)
                && !fields.focusedId.equals(fields.passwordId)) {
            addField(b, fields.focusedId, dropdown, inlinePresentation);
        }

        b.setAuthentication(pi.getIntentSender());
        return b.build();
    }

    /**
     * Dataset directo con valores reales — SIN autenticación adicional.
     * Solo se usa cuando hay una sesión transitoria válida tras una biometría
     * reciente (≤30s). Pensado para flujos multi-pantalla tipo Amazon/OpenAI
     * donde email y password están en pantallas distintas.
     */
    private Dataset buildDirectDataset(Credential c,
                                       FieldFinder.Fields fields,
                                       InlinePresentationSpec inlineSpec) {
        RemoteViews dropdown = new RemoteViews(getPackageName(), R.layout.autofill_item);
        String title = c.getTitle() != null ? c.getTitle() : "Clef";
        dropdown.setTextViewText(R.id.autofillTitle,    title);
        dropdown.setTextViewText(R.id.autofillSubtitle, safe(c.getUsername()));

        Dataset.Builder b = new Dataset.Builder(dropdown);

        InlinePresentation inlinePresentation = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineSpec != null) {
            // Aquí pasamos un PendingIntent vacío: el dataset no tiene auth,
            // pero InlineSuggestionUi.newContentBuilder requiere uno.
            PendingIntent noop = PendingIntent.getActivity(
                    this, 0,
                    new Intent(this, AutofillAuthActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            inlinePresentation = buildInlinePresentation(
                    title, safe(c.getUsername()), noop, inlineSpec);
        }

        String user = safe(c.getUsername());
        String pass = safe(c.getPassword());

        // Rellenar directamente con los valores reales (sin auth extra)
        if (fields.usernameId != null) {
            addFieldWithValue(b, fields.usernameId,
                    AutofillValue.forText(user), dropdown, inlinePresentation);
        }
        if (fields.passwordId != null) {
            addFieldWithValue(b, fields.passwordId,
                    AutofillValue.forText(pass), dropdown, inlinePresentation);
        }
        if (fields.focusedId != null
                && !fields.focusedId.equals(fields.usernameId)
                && !fields.focusedId.equals(fields.passwordId)) {
            addFieldWithValue(b, fields.focusedId,
                    AutofillValue.forText(user), dropdown, inlinePresentation);
        }

        // NO setAuthentication — este dataset ya tiene valores reales.
        return b.build();
    }

    /** Variante de addField que pasa un AutofillValue real. */
    private static void addFieldWithValue(Dataset.Builder b,
                                          AutofillId id,
                                          AutofillValue value,
                                          RemoteViews dropdown,
                                          InlinePresentation inline) {
        if (id == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inline != null) {
            b.setValue(id, value, dropdown, inline);
        } else {
            b.setValue(id, value, dropdown);
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }

    /**
     * Añade un campo al dataset. En API 30+ usa la variante con InlinePresentation
     * si está disponible; en APIs anteriores la variante básica.
     */
    private static void addField(Dataset.Builder b,
                                 AutofillId id,
                                 RemoteViews dropdown,
                                 InlinePresentation inline) {
        if (id == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inline != null) {
            // API 30+: incluye versión dropdown + inline
            b.setValue(id, null, dropdown, inline);
        } else {
            // API 28-29: solo dropdown
            b.setValue(id, null, dropdown);
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private InlinePresentation buildInlinePresentation(String title,
                                                       String subtitle,
                                                       PendingIntent attributionIntent,
                                                       InlinePresentationSpec spec) {
        try {
            Icon icon = Icon.createWithResource(this, R.drawable.ic_clef_autofill);

            android.app.slice.Slice slice = InlineSuggestionUi.newContentBuilder(attributionIntent)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setStartIcon(icon)
                    .setContentDescription("Abrir Clef para autofill")
                    .build()
                    .getSlice();

            return new InlinePresentation(slice, spec, /* pinned */ false);
        } catch (Exception e) {
            android.util.Log.w(TAG, "buildInlinePresentation failed", e);
            return null;
        }
    }

    /** Obtiene la primera InlinePresentationSpec del IME si existe. */
    private InlinePresentationSpec getFirstInlineSpec(FillRequest request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null;
        try {
            android.view.inputmethod.InlineSuggestionsRequest req =
                    request.getInlineSuggestionsRequest();
            if (req == null) return null;
            List<InlinePresentationSpec> specs = req.getInlinePresentationSpecs();
            if (specs == null || specs.isEmpty()) return null;
            return specs.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    // ── Save flow ────────────────────────────────────────────────────────────

    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {
        List<FillContext> ctxs = request.getFillContexts();
        if (ctxs.isEmpty()) { callback.onFailure("no_context"); return; }

        AssistStructure structure = ctxs.get(ctxs.size() - 1).getStructure();
        FieldFinder.Fields fields = FieldFinder.find(structure);
        if (!fields.hasPassword()) { callback.onFailure("no_password_field"); return; }

        String[] values = extractValues(structure, fields);
        String user = values[0];
        String pass = values[1];

        if (pass == null || pass.isEmpty()) {
            callback.onFailure("no_password_value"); return;
        }

        String pkg      = getCallingPackage(structure);
        String appLabel = appLabel(pkg);

        Intent intent = new Intent(this, AutofillSaveActivity.class);
        intent.putExtra(AutofillSaveActivity.EXTRA_USERNAME, user != null ? user : "");
        intent.putExtra(AutofillSaveActivity.EXTRA_PASSWORD, pass);
        intent.putExtra(AutofillSaveActivity.EXTRA_PACKAGE_NAME, pkg);
        intent.putExtra(AutofillSaveActivity.EXTRA_APP_LABEL, appLabel);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        callback.onSuccess();
    }

    private static String[] extractValues(AssistStructure structure, FieldFinder.Fields fields) {
        String[] out = {null, null};
        int n = structure.getWindowNodeCount();
        for (int i = 0; i < n; i++) {
            walkForValues(structure.getWindowNodeAt(i).getRootViewNode(), fields, out);
        }
        return out;
    }

    private static void walkForValues(AssistStructure.ViewNode node,
                                      FieldFinder.Fields fields,
                                      String[] out) {
        if (node == null) return;
        AutofillId id = node.getAutofillId();
        if (id != null) {
            AutofillValue val = node.getAutofillValue();
            if (val != null && val.isText()) {
                if (id.equals(fields.usernameId)) out[0] = val.getTextValue().toString();
                if (id.equals(fields.passwordId)) out[1] = val.getTextValue().toString();
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walkForValues(node.getChildAt(i), fields, out);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String getCallingPackage(AssistStructure structure) {
        ComponentName comp = structure.getActivityComponent();
        return comp != null ? comp.getPackageName() : null;
    }

    private String appLabel(String pkg) {
        if (pkg == null) return null;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label != null ? label.toString() : pkg;
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private String labelForHeader(String pkg) {
        String app = appLabel(pkg);
        if (app == null || app.isEmpty()) return "Clef";
        return "Clef • " + app;
    }
}
