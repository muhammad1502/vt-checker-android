plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.ninpo.scout"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        // Google Play rejects com.example IDs, so the app now lives under Ninpo's
        // namespace. Devices with the old com.example build will see two apps until
        // the old one is uninstalled.
        applicationId = "com.ninpo.scout"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // The app is written in English only; this keeps library strings (dialog
        // buttons, accessibility labels) in English too and trims the APK.
        localeFilters += listOf("en")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric's Android 16 runtime reaches into these JDK internals on Java 17+.
                it.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED"
                )
                // Screenshot tests only run when asked for: ./gradlew testDebugUnitTest -Pscreenshots
                it.systemProperty("scout.screenshots", project.hasProperty("screenshots").toString())
                it.systemProperty("scout.screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.path)
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.browser)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
