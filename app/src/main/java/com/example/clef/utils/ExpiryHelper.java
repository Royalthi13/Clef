package com.example.clef.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.clef.utils.SecurePrefs;

import androidx.core.content.ContextCompat;

import com.example.clef.R;
import com.example.clef.data.model.Credential;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ExpiryHelper {

    public static final long PERIOD_TEST         = 10L * 60 * 1000;            // 10 minutos
    public static final long PERIOD_THREE_MONTHS = 90L * 24 * 60 * 60 * 1000; // 3 meses
    public static final long PERIOD_SIX_MONTHS   = 180L * 24 * 60 * 60 * 1000;// 6 meses
    public static final long PERIOD_ONE_YEAR     = 365L * 24 * 60 * 60 * 1000;// 1 año

    public static final String PREFS_NAME         = "settings";
    public static final String PREF_NOTIFICATIONS = "notifications_enabled";
    public static final String PREF_COLORS        = "expiry_colors_enabled";
    public static final String PREF_PERIOD        = "expiry_period_ms";

    private static final String METADATA_PREFS = "expiry_metadata";
    private static final String METADATA_KEY   = "credentials_json";

    public enum Status { NONE, OK, WARNING, EXPIRED }

    /** Calcula el estado de caducidad de una credencial. */
    public static Status getStatus(long updatedAt, long periodMs) {
        if (updatedAt == 0) return Status.NONE;
        long elapsed = System.currentTimeMillis() - updatedAt;
        if (elapsed >= periodMs)       return Status.EXPIRED;
        if (elapsed >= periodMs * 0.8) return Status.WARNING;
        return Status.OK;
    }

    /** Devuelve el color de borde que corresponde al estado de caducidad. */
    public static int getStrokeColor(Context ctx, long updatedAt, long periodMs) {
        switch (getStatus(updatedAt, periodMs)) {
            case EXPIRED: return ContextCompat.getColor(ctx, R.color.expiry_expired);
            case WARNING: return ContextCompat.getColor(ctx, R.color.expiry_warning);
            default:      return ContextCompat.getColor(ctx, R.color.clef_border);
        }
    }

    // ── Metadata para WorkManager (solo título + updatedAt, sin contraseñas) ──

    public static class CredentialMeta {
        public final String title;
        public final long   updatedAt;
        public CredentialMeta(String title, long updatedAt) {
            this.title     = title;
            this.updatedAt = updatedAt;
        }
    }

    /** Guarda los metadatos de la bóveda para que el worker de fondo pueda leerlos. */
    public static void saveMetadata(Context ctx, List<Credential> credentials) {
        List<CredentialMeta> metas = new ArrayList<>();
        for (Credential c : credentials) {
            metas.add(new CredentialMeta(c.getTitle(), c.getUpdatedAt()));
        }
        String json = new Gson().toJson(metas);
        SecurePrefs.get(ctx, METADATA_PREFS)
                .edit().putString(METADATA_KEY, json).apply();
    }

    /** Carga los metadatos guardados. */
    public static List<CredentialMeta> loadMetadata(Context ctx) {
        String json = SecurePrefs.get(ctx, METADATA_PREFS)
                .getString(METADATA_KEY, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<CredentialMeta>>(){}.getType();
        List<CredentialMeta> result = new Gson().fromJson(json, type);
        return result != null ? result : new ArrayList<>();
    }
}
