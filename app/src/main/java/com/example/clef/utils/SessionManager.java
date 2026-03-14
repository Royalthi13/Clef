package com.example.clef.utils;

// SessionManager guarda la DEK (clave de cifrado) en RAM durante la sesión activa.
// La DEK es un byte[] opaco: SessionManager no sabe qué es ni cómo se generó,
// solo la guarda en memoria y la borra al hacer lock.
// El auto-lock se activa a los 60 segundos de inactividad usando un Handler.
// Tu compañero llama a setDek() tras derivar la clave, y getDek() para cifrar/descifrar.

import android.os.Handler;
import android.os.Looper;

import java.util.Arrays;

public class SessionManager {

    private static final long LOCK_TIMEOUT_MS = 60_000; // 60 segundos

    private static SessionManager instance;

    private byte[] dek = null;
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

    // Guarda la DEK en RAM y arranca el temporizador de auto-lock
    public void unlock(byte[] dek) {
        this.dek = dek;
        resetTimer();
    }

    // Devuelve la DEK si la sesión está activa, null si está bloqueada
    public byte[] getDek() {
        return dek;
    }

    // Devuelve true si hay una DEK en memoria (sesión desbloqueada)
    public boolean isUnlocked() {
        return dek != null;
    }

    // Reinicia el temporizador de auto-lock (llamar en cada interacción del usuario)
    public void resetTimer() {
        handler.removeCallbacks(lockRunnable != null ? lockRunnable : () -> {});
        lockRunnable = () -> lock();
        handler.postDelayed(lockRunnable, LOCK_TIMEOUT_MS);
    }

    // Borra la DEK de memoria y notifica al listener para ir a la pantalla de lock
    public void lock() {
        if (dek != null) {
            Arrays.fill(dek, (byte) 0); // sobrescribe antes de soltar la referencia
            dek = null;
        }
        handler.removeCallbacks(lockRunnable != null ? lockRunnable : () -> {});
        if (lockListener != null) {
            lockListener.onLock();
        }
    }

    // Registra un listener que la UI usa para redirigir a la pantalla de Master Password
    public void setOnLockListener(OnLockListener listener) {
        this.lockListener = listener;
    }

    public interface OnLockListener {
        void onLock();
    }
}
