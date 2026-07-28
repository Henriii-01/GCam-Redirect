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
        versionCode = 3
        versionName = "2.0"
    }

    flavorDimensions += "mode"
    productFlavors {
        create("stub") { dimension = "mode" }
        create("redirect") { dimension = "mode" }
    }

    flavorDimensions += "mode"
    productFlavors {
        create("stub") { dimension = "mode" }
        create("redirect") { dimension = "mode" }
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
