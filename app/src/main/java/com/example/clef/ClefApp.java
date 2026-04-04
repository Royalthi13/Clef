package com.example.clef;

import android.app.Application;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.example.clef.utils.SessionManager;
import com.example.clef.utils.ThemeManager;

public class ClefApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeManager.applyStored(this);

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
}