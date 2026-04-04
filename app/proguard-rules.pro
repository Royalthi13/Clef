# ──────────────────────────────────────────────────────────────────
# Clef — ProGuard / R8 rules
# ──────────────────────────────────────────────────────────────────

# Conservar información de línea para stack traces en producción
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── GSON ──────────────────────────────────────────────────────────
# Evita que R8 elimine los campos que GSON lee/escribe por reflexión

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Modelos del Vault — CRÍTICO: sin esto GSON devuelve objetos vacíos en release
-keep class com.example.clef.data.model.Credential { *; }
-keep class com.example.clef.data.model.Vault { *; }
-keep class com.example.clef.data.model.Credential$Category { *; }

# ── Firebase ───────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ── Lottie ────────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }

# ── Glide ─────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ── Biometría / Keystore ──────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── Lifecycle (Auto-Lock) ─────────────────────────────────────────
-keep class androidx.lifecycle.** { *; }