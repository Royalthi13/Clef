plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.clef"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.clef"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Firebase y Google Login (BOM 33.1.2 - La versión más sólida)
    implementation(platform("com.google.firebase:firebase-bom:34.10.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // GSON (Para el JSON de la Bóveda)
    implementation("com.google.code.gson:gson:2.11.0")

    // Auto-Lock de 60 segundos
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")

    // Biometría
    implementation("androidx.biometric:biometric:1.1.0")

    // Lottie (Para la animación premium)
    implementation("com.airbnb.android:lottie:6.4.1")

    implementation("com.github.bumptech.glide:glide:4.16.0")
    //Seguridad - Encriptación de datos sensibles (como contraseñas)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // WorkManager (notificaciones en background)
    implementation("androidx.work:work-runtime:2.9.0")
}