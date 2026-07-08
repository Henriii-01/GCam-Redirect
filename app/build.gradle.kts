plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

kotlin {
    jvmToolchain(21)
}

android {
    namespace = "com.google.android.apps.photos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.google.android.apps.photos"
        minSdk = 35
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }
}
