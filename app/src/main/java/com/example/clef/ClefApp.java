package com.example.clef;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.clef.utils.ThemeManager;
import com.example.clef.utils.SessionManager;

public class ClefApp extends Application {

    private static final long BACKGROUND_LOCK_DELAY_MS = 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable backgroundLockRunnable = () -> SessionManager.getInstance().lock();

    @Override
    public void onCreate() {
        super.onCreate();
        // Aplica el tema guardado antes de que se cree cualquier Activity
        ThemeManager.applyStored(this);

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(LifecycleOwner owner) {
                handler.removeCallbacks(backgroundLockRunnable);
            }

            @Override
            public void onStop(LifecycleOwner owner) {
                handler.removeCallbacks(backgroundLockRunnable);
                handler.postDelayed(backgroundLockRunnable, BACKGROUND_LOCK_DELAY_MS);
            }
        });
    }
}
