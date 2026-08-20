plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lcars.dashboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lcars.dashboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH") ?: "release.keystore"
            val ksPass = System.getenv("KEYSTORE_PASSWORD") ?: "lcars123"
            val kAlias = System.getenv("KEY_ALIAS") ?: "lcars"
            val kPass = System.getenv("KEY_PASSWORD") ?: "lcars123"
            storeFile = file(ksPath)
            storePassword = ksPass
            keyAlias = kAlias
            keyPassword = kPass
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.12.1")
}
