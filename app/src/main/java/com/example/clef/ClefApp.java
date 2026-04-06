package com.example.clef;

import android.app.Activity;
import android.app.Application;
import com.example.clef.utils.ClipboardHelper;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.clef.utils.ExpiryHelper;
import com.example.clef.utils.SecurePrefs;
import com.example.clef.utils.SessionManager;
import com.example.clef.utils.ThemeManager;
import com.example.clef.workers.PasswordExpiryWorker;

public class ClefApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyStored(this);
        createNotificationChannel();

        // Proteger todas las Activities contra capturas de pantalla y reciente de tareas
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                activity.getWindow().setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                );
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {
                ClipboardHelper.clearIfExpired(activity);
            }
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });

        // Si las notificaciones estaban activadas antes de reinstalar, reprogramar el worker
        SharedPreferences prefs = SecurePrefs.get(this, ExpiryHelper.PREFS_NAME);
        if (prefs.getBoolean(ExpiryHelper.PREF_NOTIFICATIONS, false)) {
            PasswordExpiryWorker.schedule(this);
        }

        // Cuando la app pasa a background, SessionManager arranca su propio timer.
        // Cuando vuelve a foreground, lo cancela.
        // El timeout lo configura el usuario en Ajustes (1min por defecto).
        // ClefApp ya NO tiene su propio timer de 60s hardcodeado — solo SessionManager manda.
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(LifecycleOwner owner) {
                // App en foreground — cancelar el timer de bloqueo
                SessionManager.getInstance().cancelLockTimer();
            }

            @Override
            public void onStop(LifecycleOwner owner) {
                // App en background — iniciar el timer de bloqueo con el timeout configurado
                SessionManager.getInstance().startLockTimer();
            }
        });
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                PasswordExpiryWorker.CHANNEL_ID,
                "Caducidad de contraseñas",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Avisos diarios sobre contraseñas próximas a caducar");
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}