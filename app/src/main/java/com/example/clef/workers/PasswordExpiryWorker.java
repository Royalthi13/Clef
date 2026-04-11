package com.example.clef.workers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.clef.R;
import com.example.clef.ui.dashboard.MainActivity;
import com.example.clef.utils.ExpiryHelper;
import com.example.clef.utils.SecurePrefs;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * B-4 FIX: La lógica de conteo y generación del mensaje de notificación estaba
 * duplicada en doWork() y checkAndNotify(). Ahora existe buildNotificationText()
 * como método privado compartido. Si el texto cambia, solo hay un lugar.
 */
public class PasswordExpiryWorker extends Worker {

    public static final String CHANNEL_ID  = "password_expiry";
    private static final String WORK_NAME  = "expiry_check";
    private static final String PREF_LAST_NOTIFIED = "last_notified_ms";
    private static final long   NOTIFY_COOLDOWN_MS = 2L * 60 * 1000;

    public PasswordExpiryWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = SecurePrefs.get(ctx, ExpiryHelper.PREFS_NAME);
        if (!prefs.getBoolean(ExpiryHelper.PREF_NOTIFICATIONS, false)) return Result.success();

        long periodMs = prefs.getLong(ExpiryHelper.PREF_PERIOD, ExpiryHelper.PERIOD_ONE_YEAR);
        List<ExpiryHelper.CredentialMeta> metas = ExpiryHelper.loadMetadata(ctx);

        String text = buildNotificationText(metas, periodMs);
        if (text == null) return Result.success();

        sendNotification(ctx, text);
        return Result.success();
    }

    public static void checkAndNotify(Context ctx) {
        SharedPreferences prefs = SecurePrefs.get(ctx, ExpiryHelper.PREFS_NAME);
        if (!prefs.getBoolean(ExpiryHelper.PREF_NOTIFICATIONS, false)) return;

        long now = System.currentTimeMillis();
        long lastNotified = prefs.getLong(PREF_LAST_NOTIFIED, 0);
        if (now - lastNotified < NOTIFY_COOLDOWN_MS) return;

        long periodMs = prefs.getLong(ExpiryHelper.PREF_PERIOD, ExpiryHelper.PERIOD_ONE_YEAR);
        List<ExpiryHelper.CredentialMeta> metas = ExpiryHelper.loadMetadata(ctx);

        String text = buildNotificationText(metas, periodMs);
        if (text == null) return;

        prefs.edit().putLong(PREF_LAST_NOTIFIED, now).apply();
        sendNotification(ctx, text);
    }

    // B-4 FIX: lógica unificada de conteo y construcción del mensaje.
    @Nullable
    private static String buildNotificationText(
            List<ExpiryHelper.CredentialMeta> metas, long periodMs) {
        int expired = 0, warning = 0;
        for (ExpiryHelper.CredentialMeta meta : metas) {
            ExpiryHelper.Status status = ExpiryHelper.getStatus(meta.updatedAt, periodMs);
            if      (status == ExpiryHelper.Status.EXPIRED) expired++;
            else if (status == ExpiryHelper.Status.WARNING) warning++;
        }
        if (expired == 0 && warning == 0) return null;
        if (expired > 0 && warning > 0)
            return expired + " contraseña(s) caducada(s), " + warning + " próxima(s) a caducar.";
        if (expired > 0)
            return expired + " contraseña(s) han caducado. ¡Cámbialas!";
        return warning + " contraseña(s) próximas a caducar.";
    }

    private static void sendNotification(Context ctx, String text) {
        Intent intent = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications_24)
                .setContentTitle("Clef · Contraseñas")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true);

        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1001, builder.build());
    }

    public static void schedule(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                PasswordExpiryWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    public static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME);
    }
}