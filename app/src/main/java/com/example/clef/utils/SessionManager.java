package com.example.clef.utils;

// SessionManager guarda la DEK (clave de cifrado) en RAM durante la sesión activa.
// La DEK es un byte[] opaco: SessionManager no sabe qué es ni cómo se generó,
// solo la guarda en memoria y la borra al hacer lock.
// El auto-lock se activa a los 60 segundos de inactividad usando un Handler.
// Tu compañero llama a setDek() tras derivar la clave, y getDek() para cifrar/descifrar.

import android.os.Handler;
import android.os.Looper;
import com.example.clef.data.model.Vault;
import java.util.Arrays;

public class SessionManager {

    private long lockTimeoutMs = 60_000;

    private static SessionManager instance;

    private byte[] dek   = null;
    private Vault  vault = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable lockRunnable;
    private OnLockListener lockListener;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void unlock(byte[] dek, Vault vault) {
        this.dek   = dek;
        this.vault = vault;
        resetTimer();
    }

    public byte[] getDek() {
        return dek;
    }

    public Vault getVault() {
        return vault;
    }

    public void updateVault(Vault vault) {
        this.vault = vault;
    }

    public boolean isUnlocked() {
        return dek != null;
    }

    public void setLockTimeout(long ms) {
        lockTimeoutMs = ms;
    }

    public void resetTimer() {
        handler.removeCallbacks(lockRunnable != null ? lockRunnable : () -> {});
        lockRunnable = () -> lock();
        handler.postDelayed(lockRunnable, lockTimeoutMs);
    }

    public void lock() {
        if (dek != null) {
            Arrays.fill(dek, (byte) 0);
            dek = null;
        }
        vault = null;
        handler.removeCallbacks(lockRunnable != null ? lockRunnable : () -> {});
        if (lockListener != null) {
            lockListener.onLock();
        }
    }

    public void setOnLockListener(OnLockListener listener) {
        this.lockListener = listener;
    }

    public interface OnLockListener {
        void onLock();
    }
}
