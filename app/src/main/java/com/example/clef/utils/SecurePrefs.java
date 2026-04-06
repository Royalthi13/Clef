package com.example.clef.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Proveedor centralizado de SharedPreferences cifradas.
 * Usa AES256-GCM para valores y AES256-SIV para claves.
 * Si el cifrado falla (p.ej. dispositivo sin hardware seguro), cae a prefs normales.
 */
public class SecurePrefs {

    private SecurePrefs() {}

    public static SharedPreferences get(Context context, String name) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    name,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            return context.getSharedPreferences(name, Context.MODE_PRIVATE);
        }
    }
}
