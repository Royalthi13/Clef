package com.example.clef.utils;

import android.os.Handler;
import android.os.Looper;

import com.example.clef.data.model.Vault;
import com.example.clef.crypto.CryptoUtils;

import java.util.Arrays;

/**
 * GetDek() sigue devolviendo un clon (necesario para hilos de fondo),
 * pero ahora existe zeroizeDekCopy() para que los llamadores limpien su copia
 * de forma explícita. Se añade withDek() para operaciones en hilo principal
 * que no necesitan capturar la DEK en un lambda de fondo.
 *
 * Patrón correcto en hilos de fondo:
 *   byte[] dek = session.getDek();
 *   executor.execute(() -> {
 *       try { ... usar dek ... }
 *       finally { SessionManager.zeroizeDekCopy(dek); }
 *   });
 */
public class SessionManager {

    private static volatile SessionManager instance;

    private volatile byte[] dek = null;
    private volatile Vault vault = null;
    private long lockTimeoutMs = 300_000;
    private long cloudVaultVersion   = -1;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable lockRunnable;
    private OnLockListener lockListener;

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) instance = new SessionManager();
            }
        }
        return instance;
    }

    // Sesión

    public synchronized void unlock(byte[] dek, Vault vault) {
        if (this.dek != null) CryptoUtils.zeroise(this.dek);
        this.dek   = dek;
        this.vault = vault;
        resetInactivityTimer();
    }

    /**
     * Devuelve un clon de la DEK.
     * El llamador DEBE zerizar el array devuelto tras usarlo con zeroizeDekCopy().
     */
    public synchronized byte[] getDek() {
        return dek != null ? dek.clone() : null;
    }

    /**
     * Helper para zerizar las copias de DEK creadas por getDek().
     * Llamar siempre en el bloque finally de los ejecutores.
     */
    public static void zeroizeDekCopy(byte[] dekCopy) {
        CryptoUtils.zeroise(dekCopy);
    }

    public synchronized Vault getVault() { return vault; }
    public boolean isUnlocked() { return dek != null; }

    public long getCloudVaultVersion() { return cloudVaultVersion; }
    public synchronized void setCloudVaultVersion(long version) { cloudVaultVersion = version; }

    public synchronized void updateVault(Vault vault) { this.vault = vault; }

    // Timer de bloqueo

    public void setLockTimeout(long ms) { this.lockTimeoutMs = ms; }

    public void cancelLockTimer() {
        if (lockRunnable != null) {
            handler.removeCallbacks(lockRunnable);
            lockRunnable = null;
        }
    }

    public void startLockTimer() {
        cancelLockTimer();
        if (lockTimeoutMs == Long.MAX_VALUE) return;
        if (!isUnlocked()) return;
        lockRunnable = this::lock;
        handler.postDelayed(lockRunnable, lockTimeoutMs);
    }

    public void resetTimer() {
        cancelLockTimer();
        startLockTimer();
    }

    //  Bloqueo

    public synchronized void lock() {
        cancelLockTimer();
        if (dek != null) {
            Arrays.fill(dek, (byte) 0x00);
            dek = null;
        }
        vault = null;
        if (lockListener != null) {
            handler.post(() -> { if (lockListener != null) lockListener.onLock(); });
        }
    }

    // Callback

    public void setOnLockListener(OnLockListener listener) { this.lockListener = listener; }

    public interface OnLockListener { void onLock(); }

    // Privado

    private void resetInactivityTimer() {
        cancelLockTimer();
        if (lockTimeoutMs != Long.MAX_VALUE && isUnlocked()) {
            lockRunnable = this::lock;
            handler.postDelayed(lockRunnable, lockTimeoutMs);
        }
    }
}