plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.tex.cruscotto"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.tex.cruscotto"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing comes from environment variables (set by the release workflow from GitHub secrets).
    // Without them, release builds are left unsigned.
    val keystorePath = System.getenv("CRUSCOTTO_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CRUSCOTTO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CRUSCOTTO_KEY_ALIAS")
                keyPassword = System.getenv("CRUSCOTTO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            // Only 64-bit ARM phones: drops the other native SQLCipher builds and makes the APK much smaller.
            // Debug builds keep every ABI, so the emulator on a PC still works.
            ndk { abiFilters += listOf("arm64-v8a") }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // No dependency metadata blob inside the APK (it is only useful to Play Store scans).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    androidResources {
        // Only the languages the app is translated into: library strings in other languages are dropped.
        localeFilters += listOf("en", "de", "it")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation("net.zetetic:sqlcipher-android:${libs.versions.sqlcipher.get()}@aar")

    // Installs the Compose libraries' startup profiles even when the APK is sideloaded: faster first frames.
    implementation(libs.androidx.profileinstaller)

    implementation(libs.androidx.biometric)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
}
