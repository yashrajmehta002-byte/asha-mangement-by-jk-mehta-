plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ashamanagement.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ashamanagement.app"
        minSdk = 26
        targetSdk = 34
        // हर नए अपडेट पर versionCode बढ़ाएं (1 -> 2 -> 3 ...) और versionName बदलें
        versionCode = 1
        versionName = "1.0.0"
    }

    // एक ही कुंजी (keystore) से हर बार APK साइन होती है, तभी पुराने ऐप के ऊपर अपडेट इंस्टॉल हो पाता है।
    // इस फ़ाइल (asha-release.jks) को कभी न खोएं और न बदलें।
    signingConfigs {
        create("release") {
            storeFile = file("asha-release.jks")
            storePassword = "ashamgmt2026"
            keyAlias = "asha"
            keyPassword = "ashamgmt2026"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.webkit:webkit:1.12.1")
}
