plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.tamodak.killit"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.tamodak.killit"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // How long "unblock all & remove admin" waits before it can be carried out. The delay is
        // the feature: it puts distance between the decision and its effect, so a moment of
        // weakness cannot undo everything with one tap.
        //
        // Three seconds while developing, because otherwise the flow is untestable. Overridden
        // below for release.
        buildConfigField("long", "RELEASE_DELAY_MILLIS", "3_000L")
    }

    buildTypes {
        release {
            optimization {
                // Code shrinking, obfuscation and resource shrinking. Keep rules that survive it
                // live in src/main/keepRules/.
                enable = true
            }
            // Three days.
            buildConfigField("long", "RELEASE_DELAY_MILLIS", "259_200_000L")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        aidl = true
        // KillitPrivilegedService rebuilds the admin component from BuildConfig.APPLICATION_ID
        // rather than trusting one passed over binder.
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.zxing.core)
    implementation(libs.zxing.embedded)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}