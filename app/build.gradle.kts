plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.wolfwheels.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wolfwheels.app"
        minSdk = 23
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Only phone processors (ARM). x86 is emulators and almost no real phones: it doubled the native libraries.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Same key on every build so updates install on top of the previous version.
    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "noirwheels2026"
            keyAlias = "noirwheels"
            keyPassword = "noirwheels2026"
        }
    }

    buildTypes {
        release {
            // R8 removes the library code the app never uses. No renaming (-dontobfuscate),
            // so crash reports stay readable, and all of the app's own classes are kept as they are.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    // Native libraries compressed inside the APK: much smaller download (they are unpacked on install).
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += listOf("META-INF/*.kotlin_module", "META-INF/*.version", "kotlin/**", "DebugProbesKt.bin", "META-INF/LICENSE*", "META-INF/NOTICE*") }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // Daily news check in the background
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jsoup:jsoup:1.18.1")
    // Translate the wiki texts to Spanish on the phone (the language is downloaded once)
    implementation("com.google.mlkit:translate:17.0.3")
    // Read the toy number on the card from a photo
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
}
