plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cartv.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cartv.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // Fixed key so every build can install over the previous one (keeps your sign-ins).
    signingConfigs {
        create("cartv") {
            storeFile = file("cartv.keystore")
            storePassword = "cartvpass"
            keyAlias = "cartv"
            keyPassword = "cartvpass"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("cartv") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("cartv")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")
}
