package com.example.clef.autofill;

import android.app.assist.AssistStructure;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.autofill.AutofillId;

import androidx.annotation.RequiresApi;

/**
 * Parsea un AssistStructure y localiza campos candidatos a username/password.
 *
 * Prioridades de detección (por nodo):
 *   1. autofillHints (el desarrollador lo declaró).
 *   2. HTML input types + HTML autocomplete.
 *   3. Android InputType (TYPE_TEXT_VARIATION_PASSWORD / EMAIL_ADDRESS).
 *   4. Heurística por idEntry / hint / contentDescription del nodo.
 *   5. (Nuevo) Label del TextView ANTERIOR al EditText — patrón común en formularios
 *      como "Email:" encima del campo. Se guarda mientras se recorre el árbol.
 *
 * Además rastrea qué nodo tiene el FOCO — si el foco cae sobre algo que no es
 * username ni password, el servicio necesita ese id para que Android acepte
 * mostrar el popup de autofill (requisito del framework).
 */
@RequiresApi(Build.VERSION_CODES.O)
public final class FieldFinder {

    public static class Fields {
        public AutofillId usernameId;
        public AutofillId passwordId;
        public String hintUsername;  // debug
        public String hintPassword;  // debug
        public AutofillId focusedId;

        public boolean hasPassword() { return passwordId != null; }
        public boolean hasUsername() { return usernameId != null; }
        public boolean hasAny()      { return usernameId != null || passwordId != null; }
    }

    private static final String[] USERNAME_HINTS = {
            View.AUTOFILL_HINT_USERNAME,
            View.AUTOFILL_HINT_EMAIL_ADDRESS,
            "username", "user_name", "user", "email", "e-mail", "mail",
            "login", "usuario", "correo", "identifier", "account", "signin"
    };

    private static final String[] PASSWORD_HINTS = {
            View.AUTOFILL_HINT_PASSWORD,
            "password", "passwd", "pwd", "pass",
            "contraseña", "contrasena", "clave", "secret"
    };

    private FieldFinder() {}

    public static Fields find(AssistStructure structure) {
        Fields out = new Fields();
        int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            WalkState state = new WalkState();
            walk(structure.getWindowNodeAt(i).getRootViewNode(), out, state);
        }
        return out;
    }

    /** Estado que acompaña al recorrido: recordar el último texto visible. */
    private static class WalkState {
        String lastLabelText;  // texto del TextView justo antes del próximo EditText
    }

    private static void walk(AssistStructure.ViewNode node, Fields out, WalkState state) {
        if (node == null) return;

        // Si ya tenemos todo, podemos parar.
        if (out.usernameId != null && out.passwordId != null && out.focusedId != null) {
            return;
        }

        // Respetar IMPORTANT_FOR_AUTOFILL_NO, pero permitir descendientes
        int iforA = node.getImportantForAutofill();
        boolean skipSelf = iforA == View.IMPORTANT_FOR_AUTOFILL_NO
                || iforA == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;
        boolean skipChildren = iforA == View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS;

        if (!skipSelf) inspect(node, out, state);

        if (!skipChildren) {
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) walk(node.getChildAt(i), out, state);
        }
    }

    private static void inspect(AssistStructure.ViewNode node, Fields out, WalkState state) {
        AutofillId id = node.getAutofillId();

        // Rastrear el foco SIEMPRE (independiente de webview, clase, etc.)
        if (id != null && node.isFocused() && out.focusedId == null) {
            out.focusedId = id;
        }

        if (id == null) return;

        // Actualizar "último label visto" si este nodo es un TextView (no editable)
        CharSequence txt = node.getText();
        String className = node.getClassName();
        boolean isEditable = className != null && className.contains("EditText");
        if (!isEditable && txt != null && txt.length() > 0 && txt.length() < 40) {
            state.lastLabelText = txt.toString();
        }

        // Ignorar nodos dentro de WebViews embebidos: el usuario eligió solo apps nativas.
        if (node.getWebDomain() != null) return;

        // 1) autofillHints explícitos
        String[] hints = node.getAutofillHints();
        if (hints != null) {
            for (String h : hints) {
                if (out.passwordId == null && matches(h, PASSWORD_HINTS)) {
                    out.passwordId = id;
                    out.hintPassword = h;
                    state.lastLabelText = null;
                    return;
                }
                if (out.usernameId == null && matches(h, USERNAME_HINTS)) {
                    out.usernameId = id;
                    out.hintUsername = h;
                    state.lastLabelText = null;
                    return;
                }
            }
        }

        // 2) InputType
        int input = node.getInputType();
        int cls = input & InputType.TYPE_MASK_CLASS;
        int var = input & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_TEXT) {
            boolean isPassword =
                    var == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                            var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                            var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
            if (isPassword && out.passwordId == null) {
                out.passwordId = id;
                out.hintPassword = "inputType=password";
                state.lastLabelText = null;
                return;
            }
            boolean isEmail =
                    var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                            var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
            if (isEmail && out.usernameId == null) {
                out.usernameId = id;
                out.hintUsername = "inputType=email";
                state.lastLabelText = null;
                return;
            }
        }

        // 3+4+5) Heurística combinada: propio texto + label precedente
        String combined = (
                safe(node.getIdEntry()) + " " +
                        safe(asString(node.getHint())) + " " +
                        safe(asString(node.getContentDescription())) + " " +
                        safe(state.lastLabelText)
        ).toLowerCase();

        if (!combined.trim().isEmpty() && isEditable) {
            if (out.passwordId == null && containsAny(combined, PASSWORD_HINTS)) {
                out.passwordId = id;
                out.hintPassword = "heuristic";
                state.lastLabelText = null;
                return;
            }
            if (out.usernameId == null && containsAny(combined, USERNAME_HINTS)) {
                out.usernameId = id;
                out.hintUsername = "heuristic";
                state.lastLabelText = null;
            }
        }
    }

    private static boolean matches(String hint, String[] table) {
        if (hint == null) return false;
        String h = hint.toLowerCase();
        for (String t : table) if (h.equals(t.toLowerCase())) return true;
        return false;
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String n : needles) {
            if (n.length() < 3) continue;
            if (haystack.contains(n.toLowerCase())) return true;
        }
        return false;
    }

    private static String asString(CharSequence cs) { return cs == null ? "" : cs.toString(); }
    private static String safe(String s) { return s == null ? "" : s; }
}
