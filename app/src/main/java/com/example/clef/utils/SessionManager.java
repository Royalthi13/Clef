package com.example.clef.utils;

import android.os.Handler;
import android.os.Looper;

import com.example.clef.data.model.Vault;

import java.util.Arrays;

public class SessionManager {

    private static volatile SessionManager instance; // volatile para visibilidad entre hilos

    private byte[] dek   = null;
    private Vault  vault = null;
    private long   lockTimeoutMs = 60_000;

    private final Handler  handler = new Handler(Looper.getMainLooper());
    private Runnable       lockRunnable;
    private OnLockListener lockListener;

    private SessionManager() {}

    /** Double-checked locking — seguro y sin overhead tras la primera inicialización. */
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

    public void unlock(byte[] dek, Vault vault) {
        this.dek   = dek;
        this.vault = vault;
        resetTimer();
    }

    public byte[] getDek()   { return dek; }
    public Vault  getVault() { return vault; }

    public void updateVault(Vault vault) { this.vault = vault; }

    public boolean isUnlocked() { return dek != null; }

    public void setLockTimeout(long ms) { lockTimeoutMs = ms; }

    public void resetTimer() {
        if (lockRunnable != null) handler.removeCallbacks(lockRunnable);
        if (lockTimeoutMs == Long.MAX_VALUE) return;
        lockRunnable = this::lock;
        handler.postDelayed(lockRunnable, lockTimeoutMs);
    }

    public void lock() {
        if (dek != null) {
            Arrays.fill(dek, (byte) 0);
            dek = null;
        }
        vault = null;
        if (lockRunnable != null) handler.removeCallbacks(lockRunnable);
        if (lockListener != null) lockListener.onLock();
    }

    public void setOnLockListener(OnLockListener listener) { this.lockListener = listener; }

    public interface OnLockListener { void onLock(); }
}