package com.example.clef;

import android.app.Application;

import com.example.clef.utils.ThemeManager;

public class ClefApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Aplica el tema guardado antes de que se cree cualquier Activity
        ThemeManager.applyStored(this);
    }
}
