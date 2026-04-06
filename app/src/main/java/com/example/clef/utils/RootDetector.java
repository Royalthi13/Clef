package com.example.clef.utils;

import android.content.Context;
import android.content.pm.PackageManager;

import java.io.File;

/**
 * Detecta indicios de root en el dispositivo.
 *
 * Dos categorias:
 * - Indicios CLAROS: uno solo es suficiente para bloquear
 * - Indicios SOSPECHOSOS: se necesitan 3 o mas para bloquear
 */
public class RootDetector {

    // Apps de root conocidas
    private static final String[] ROOT_APPS = {
            "com.topjohnwu.magisk",       // Magisk
            "eu.chainfire.supersu",        // SuperSU
            "com.koushikdutta.superuser",  // Superuser
            "com.noshufou.android.su",     // Superuser (antiguo)
            "com.kingroot.kinguser",       // KingRoot
            "com.kingo.root",              // KingoRoot
            "com.smedialink.oneclickroot", // One Click Root
            "com.zhiqupk.root.global",     // Root Master
            "com.alephzain.framaroot"      // Framaroot
    };

    // Rutas donde se suele encontrar el binario su (indicios claros)
    private static final String[] SU_CLEAR_PATHS = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su"
    };

    // Rutas sospechosas adicionales
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
        public final boolean isClear; // true = indicio claro, false = sospechoso

        Result(boolean blocked, String reason, boolean isClear) {
            this.blocked = blocked;
            this.reason  = reason;
            this.isClear = isClear;
        }
    }

    /**
     * Analiza el dispositivo y devuelve el resultado.
     */
    public static Result check(Context context) {
        // ── Indicios CLAROS (uno solo bloquea) ────────────────────────────────

        // 1. Binario su en rutas principales
        for (String path : SU_CLEAR_PATHS) {
            if (new File(path).exists()) {
                return new Result(true,
                        "Se ha detectado acceso de superusuario (su) en el dispositivo. " +
                        "Ruta: " + path,
                        true);
            }
        }

        // 2. App de root instalada
        String rootApp = findRootApp(context);
        if (rootApp != null) {
            return new Result(true,
                    "Se ha detectado una aplicación de root instalada: " + rootApp + ". " +
                    "Esto indica que el dispositivo ha sido rooteado.",
                    true);
        }

        // 3. Particion del sistema escribible
        if (isSystemWritable()) {
            return new Result(true,
                    "La partición del sistema es modificable. " +
                    "Esto indica que el dispositivo ha sido rooteado.",
                    true);
        }

        // ── Indicios SOSPECHOSOS (necesitan 3 para bloquear) ──────────────────

        int suspiciousCount = 0;
        StringBuilder suspiciousDetails = new StringBuilder();

        // 4. Binario su en rutas secundarias
        for (String path : SU_SUSPICIOUS_PATHS) {
            if (new File(path).exists()) {
                suspiciousCount++;
                suspiciousDetails.append("• Binario su en ").append(path).append("\n");
                break;
            }
        }

        // 5. Build de test-keys (sistema no firmado por fabricante oficial)
        String buildTags = android.os.Build.TAGS;
        if (buildTags != null && (buildTags.contains("test-keys") ||buildTags.contains("dev-keys") )) {
            suspiciousCount++;
            suspiciousDetails.append("• Sistema firmado con claves de prueba (test-keys)\n");
        }

        // 6. ro.debuggable = 1
        if ("1".equals(getBuildProp("ro.debuggable"))) {
            suspiciousCount++;
            suspiciousDetails.append("• Sistema operativo en modo debug\n");
        }

        // 7. ro.secure = 0
        if ("0".equals(getBuildProp("ro.secure"))) {
            suspiciousCount++;
            suspiciousDetails.append("• Seguridad del sistema desactivada (ro.secure=0)\n");
        }

        if (suspiciousCount >= 3) {
            return new Result(true,
                    "Se han detectado " + suspiciousCount + " indicios sospechosos de modificación:\n\n" +
                    suspiciousDetails.toString().trim(),
                    false);
        }

        return new Result(false, "", false);
    }

    private static String findRootApp(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String pkg : ROOT_APPS) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (PackageManager.NameNotFoundException ignored) {}
        }
        return null;
    }

    private static boolean isSystemWritable() {
        try {
            Process p = Runtime.getRuntime().exec("mount");
            byte[] bytes = new byte[1024];
            int read = p.getInputStream().read(bytes);
            if (read > 0) {
                String mount = new String(bytes, 0, read);
                for (String line : mount.split("\n")) {
                    if (line.contains("/system") && line.contains("rw")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static String getBuildProp(String prop) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", prop});
            byte[] bytes = new byte[64];
            int read = p.getInputStream().read(bytes);
            if (read > 0) return new String(bytes, 0, read).trim();
        } catch (Exception ignored) {}
        return "";
    }
}
