plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.securecam.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.securecam.app"
        minSdk = 26        // Android 8.0 — notification channels + CameraX baseline
        targetSdk = 35     // Android 15 — required for Play submissions from Aug 2025
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 shrink/obfuscate; resource shrinking keeps the APK lean.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---- Kotlin & lifecycle (MVVM) -----------------------------------------
    val lifecycleVersion = "2.8.7"
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-service:$lifecycleVersion")

    // ---- Jetpack Compose (Material 3) ---------------------------------------
    val composeBom = "androidx.compose:compose-bom:2024.12.01"
    implementation(platform(composeBom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Navigation ----------------------------------------------------------
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ---- Image loading (gallery thumbnails) ---------------------------------
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ---- CameraX (latest stable; 1.6.x includes RAW/DNG capture) -------------
    val cameraxVersion = "1.6.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
