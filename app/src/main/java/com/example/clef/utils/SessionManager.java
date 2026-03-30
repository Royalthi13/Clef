package com.example.clef.utils;

import android.os.Handler;
import android.os.Looper;

import com.example.clef.data.model.Vault;

import java.util.Arrays;

public class SessionManager {

    private static volatile SessionManager instance;

    private byte[] dek   = null;
    private Vault  vault = null;
    private long   lockTimeoutMs = 60_000;

    private final Handler  handler = new Handler(Looper.getMainLooper());
    private Runnable       lockRunnable;
    private OnLockListener lockListener;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    // ── Sesión ─────────────────────────────────────────────────────────────────

    public void unlock(byte[] dek, Vault vault) {
        this.dek   = dek;
        this.vault = vault;
        // Al desbloquear, resetear el timer de inactividad
        resetInactivityTimer();
    }

    public byte[]  getDek()        { return dek; }
    public Vault   getVault()      { return vault; }
    public boolean isUnlocked()    { return dek != null; }
    public void    updateVault(Vault vault) { this.vault = vault; }

    // ── Timer de bloqueo ───────────────────────────────────────────────────────

    /** Configura el tiempo de inactividad antes del bloqueo automático. */
    public void setLockTimeout(long ms) {
        this.lockTimeoutMs = ms;
    }

    /**
     * Cancela el timer activo (app vuelve a foreground).
     * Llamado desde ClefApp.onStart().
     */
    public void cancelLockTimer() {
        if (lockRunnable != null) {
            handler.removeCallbacks(lockRunnable);
            lockRunnable = null;
        }
    }

    /**
     * Inicia el timer de bloqueo por background (app pasa a background).
     * Llamado desde ClefApp.onStop().
     */
    public void startLockTimer() {
        cancelLockTimer(); // Evitar duplicados
        if (lockTimeoutMs == Long.MAX_VALUE) return; // "Nunca" seleccionado
        if (!isUnlocked()) return; // Ya bloqueado, no hace falta

        lockRunnable = this::lock;
        handler.postDelayed(lockRunnable, lockTimeoutMs);
    }

    /**
     * Reinicia el timer de inactividad (se llama al desbloquear o cuando el usuario actúa).
     * Distinto de startLockTimer: este se usa para la inactividad dentro de la app,
     * startLockTimer para cuando la app pasa a background.
     *
     * @deprecated Usa startLockTimer/cancelLockTimer para el ciclo de vida de la app.
     * Este método se mantiene para compatibilidad con código existente.
     */
    public void resetTimer() {
        cancelLockTimer();
        startLockTimer();
    }

    // ── Bloqueo ────────────────────────────────────────────────────────────────

    public void lock() {
        cancelLockTimer();
        if (dek != null) {
            Arrays.fill(dek, (byte) 0x00);
            dek = null;
        }
        vault = null;
        if (lockListener != null) lockListener.onLock();
    }

    // ── Callback ───────────────────────────────────────────────────────────────

    public void setOnLockListener(OnLockListener listener) {
        this.lockListener = listener;
    }

    public interface OnLockListener {
        void onLock();
    }

    // ── Privado ────────────────────────────────────────────────────────────────

    private void resetInactivityTimer() {
        cancelLockTimer();
        // Solo reiniciar si la sesión está abierta y hay un timeout configurado
        if (lockTimeoutMs != Long.MAX_VALUE && isUnlocked()) {
            lockRunnable = this::lock;
            handler.postDelayed(lockRunnable, lockTimeoutMs);
        }
    }
}