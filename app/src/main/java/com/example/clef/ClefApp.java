package com.example.clef;

import android.app.Activity;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.clef.utils.ClipboardHelper;
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


        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                // A-5 FIX: aplicar antes de que la ventana sea visible.
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

        SharedPreferences prefs = SecurePrefs.get(this, ExpiryHelper.PREFS_NAME);
        if (prefs.getBoolean(ExpiryHelper.PREF_NOTIFICATIONS, false)) {
            PasswordExpiryWorker.schedule(this);
        }

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(LifecycleOwner owner) {
                SessionManager.getInstance().cancelLockTimer();
            }
            @Override
            public void onStop(LifecycleOwner owner) {
                SessionManager.getInstance().startLockTimer();
            }
        });
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                PasswordExpiryWorker.CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getString(R.string.notif_channel_desc));
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }
}