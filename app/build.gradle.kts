plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.stalkerapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stalkerapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"
    }

    signingConfigs {
        create("release") {
            // Keystore CI'da GitHub Secrets üzerinden sağlanır (KEYSTORE_BASE64
            // decode edilip KEYSTORE_PATH olarak verilir); yerelde keystore/ altından
            // okunur. Şifre env'den yoksa yerel varsayılan kullanılır.
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: rootProject.file("keystore/release.p12").path)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "stalkerapp2026"
            keyAlias = "stalkerapp"
            keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: "stalkerapp2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.cast)
    implementation(libs.androidx.media)
    implementation(libs.google.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    // Firebase: hesap girişi (e-posta/şifre + Google) ve bulut yedek senkronu
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
