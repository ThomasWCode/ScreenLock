plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.thomaswcode.screenlock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thomaswcode.screenlock"
        // Android 14: an accessibility service can take in touchscreen events (setMotionEventSources),
        // which is what the lock is built on.
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // This app's own key, kept out of the public repository (git-ignored): it signs an app with an
        // accessibility service that can block the touchscreen, so a published key would let anyone build
        // an update that installs over it. Without the file (CI, a fresh clone) the default debug key is used.
        if (file("debug.keystore").exists()) {
            getByName("debug") {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
