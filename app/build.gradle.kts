plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.viddown.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.viddown.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Most modern Android devices (Android 8+) are arm64-v8a. Dropping
        // armeabi-v7a removes a second full copy of the bundled python/
        // ffmpeg/aria2c native binaries, cutting the APK size roughly in half.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { isDebuggable = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs {
            pickFirsts += listOf("**/*.so")
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Coil (Image loading)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ─── Seal Engine (Updated) ───────────────────────────────────────
    // youtubedl-android: yt-dlp wrapper (Latest stable version from Maven Central)
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    // FFmpeg bundled with youtubedl-android
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    // Aria2c for accelerated multi-threaded downloads
    implementation("io.github.junkfood02.youtubedl-android:aria2c:0.18.1")
    // OkHttp 5 for network calls
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.11")
    // ──────────────────────────────────────────────────────────────

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
