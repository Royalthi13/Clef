package com.example.clef.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runtime.exec() ahora tiene timeout de 500ms por llamada.
 * Se usan hilos de fondo con ExecutorService para no bloquear el hilo llamador.
 * Además, el buffer de lectura de "mount" se amplía a 4096 bytes para no
 * truncar la lista de puntos de montaje en sistemas con muchas particiones.
 */
public class RootDetector {

    private static final int EXEC_TIMEOUT_MS = 500;

    private static final String[] ROOT_APPS = {
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot"
    };

    private static final String[] SU_CLEAR_PATHS = {
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su"
    };

    private static final String[] SU_SUSPICIOUS_PATHS = {
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/dev/com.koushikdutta.superuser.daemon/"
    };

    public static class Result {
        public final boolean blocked;
        public final String  reason;
        public final boolean isClear;
        Result(boolean blocked, String reason, boolean isClear) {
            this.blocked = blocked; this.reason = reason; this.isClear = isClear;
        }
    }

    public static Result check(Context context) {
        for (String path : SU_CLEAR_PATHS) {
            if (new File(path).exists()) {
                return new Result(true, "Binario su detectado en " + path, true);
            }
        }

        String rootApp = findRootApp(context);
        if (rootApp != null) {
            return new Result(true, "App de root instalada: " + rootApp, true);
        }

        if (isSystemWritable()) {
            return new Result(true, "La partición del sistema es modificable.", true);
        }

        int suspiciousCount = 0;
        StringBuilder details = new StringBuilder();

        for (String path : SU_SUSPICIOUS_PATHS) {
            if (new File(path).exists()) {
                suspiciousCount++;
                details.append("• Binario su en ").append(path).append("\n");
                break;
            }
        }

        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && (buildTags.contains("test-keys") || buildTags.contains("dev-keys"))) {
            suspiciousCount++;
            details.append("• Sistema firmado con claves de prueba\n");
        }
        if ("1".equals(getBuildProp("ro.debuggable"))) {
            suspiciousCount++;
            details.append("• Sistema en modo debug\n");
        }
        if ("0".equals(getBuildProp("ro.secure"))) {
            suspiciousCount++;
            details.append("• ro.secure=0\n");
        }

        if (suspiciousCount >= 3) {
            return new Result(true,
                    suspiciousCount + " indicios de root:\n" + details.toString().trim(),
                    false);
        }
        return new Result(false, "", false);
    }

    private static String findRootApp(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : ROOT_APPS) {
            try { pm.getPackageInfo(pkg, 0); return pkg; }
            catch (PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }

    // timeout de 500ms y buffer de 4096 bytes.
    private static boolean isSystemWritable() {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> future = exec.submit(() -> {
                Process p = Runtime.getRuntime().exec("mount");
                byte[] bytes = new byte[4096];
                int read = p.getInputStream().read(bytes);
                p.destroy();
                if (read <= 0) return false;
                String mount = new String(bytes, 0, read);
                for (String line : mount.split("\n")) {
                    if (line.contains("/system") && line.contains(" rw")) return true;
                }
                return false;
            });
            return future.get(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return false; // timeout - asumir no modificable por seguridad
        } catch (Exception e) {
            return false;
        } finally {
            exec.shutdownNow();
        }
    }

    // timeout de 500ms.
    private static String getBuildProp(String prop) {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = exec.submit(() -> {
                Process p = Runtime.getRuntime().exec(new String[]{"getprop", prop});
                byte[] bytes = new byte[64];
                int read = p.getInputStream().read(bytes);
                p.destroy();
                return (read > 0) ? new String(bytes, 0, read).trim() : "";
            });
            return future.get(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return "";
        } finally {
            exec.shutdownNow();
        }
    }
}
