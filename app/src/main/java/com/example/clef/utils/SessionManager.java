package com.example.clef.utils;

import android.os.Handler;
import android.os.Looper;

import com.example.clef.data.model.Vault;

import java.util.Arrays;

/**
 * Almacena la DEK activa y el Vault en memoria durante la sesión.
 *
 * Thread-safety:
 *   - dek y vault son accedidos desde el hilo principal Y desde hilos de crypto.
 *   - Toda escritura se hace con synchronized para evitar race conditions.
 *   - updateVault() hace un post al hilo principal para que la UI siempre lea
 *     en el mismo hilo, y también actualiza en el hilo llamador para que
 *     el código de fondo vea el estado actualizado inmediatamente.
 */
public class SessionManager {

    private static volatile SessionManager instance;

    private volatile byte[] dek   = null;
    private volatile Vault  vault = null;
    private long   lockTimeoutMs    = 300_000;
    private long   cloudVaultVersion = -1; // -1 = desconocido

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

    public synchronized void unlock(byte[] dek, Vault vault) {
        this.dek   = dek;
        this.vault = vault;
        resetInactivityTimer();
    }

    public synchronized byte[] getDek() {
        return dek != null ? dek.clone() : null;
    }
    public synchronized Vault   getVault()   { return vault; }
    public boolean isUnlocked() { return dek != null; }

    public long getCloudVaultVersion() { return cloudVaultVersion; }
    public synchronized void setCloudVaultVersion(long version) { this.cloudVaultVersion = version; }

    /**
     * Actualiza el vault en memoria de forma thread-safe.
     *
     * Puede llamarse desde cualquier hilo (hilo de crypto o principal).
     * La actualización es inmediata en el hilo llamador y también se
     * propaga al hilo principal para que la UI esté siempre al día.
     */
    public synchronized void updateVault(Vault vault) {
        this.vault = vault;
    }

    // ── Timer de bloqueo ───────────────────────────────────────────────────────

    public void setLockTimeout(long ms) {
        this.lockTimeoutMs = ms;
    }

    /** Cancela el timer activo (app vuelve a foreground). */
    public void cancelLockTimer() {
        if (lockRunnable != null) {
            handler.removeCallbacks(lockRunnable);
            lockRunnable = null;
        }
    }

    /** Inicia el timer de bloqueo cuando la app pasa a background. */
    public void startLockTimer() {
        cancelLockTimer();
        if (lockTimeoutMs == Long.MAX_VALUE) return; // "Nunca"
        if (!isUnlocked()) return;                   // Ya bloqueado

        lockRunnable = this::lock;
        handler.postDelayed(lockRunnable, lockTimeoutMs);
    }

    /**
     * Reinicia el timer de inactividad.
     * @deprecated Usar startLockTimer/cancelLockTimer para el ciclo de vida.
     */
    public void resetTimer() {
        cancelLockTimer();
        startLockTimer();
    }

    // ── Bloqueo ────────────────────────────────────────────────────────────────

    public synchronized void lock() {
        cancelLockTimer();
        if (dek != null) {
            Arrays.fill(dek, (byte) 0x00);
            dek = null;
        }
        vault = null;
        if (lockListener != null) {
            // Siempre disparar en el hilo principal para poder navegar
            handler.post(() -> {
                if (lockListener != null) lockListener.onLock();
            });
        }
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
        if (lockTimeoutMs != Long.MAX_VALUE && isUnlocked()) {
            lockRunnable = this::lock;
            handler.postDelayed(lockRunnable, lockTimeoutMs);
        }
    }
}