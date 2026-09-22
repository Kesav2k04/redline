import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "dev.kesav.redline"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.kesav.redline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // Read from local.properties, which is not committed. Absent key means the
        // app still builds and runs from a clean clone, with the paywall locked.
        buildConfigField(
            "String",
            "REVENUECAT_API_KEY",
            "\"${localProperties.getProperty("revenuecat.apiKey", "")}\"",
        )
    }

    // Only configured when a keystore is named in local.properties, so a clean clone
    // still builds a release variant without anyone's signing material.
    val keystore = localProperties.getProperty("release.storeFile")?.let(::file)

    signingConfigs {
        if (keystore != null && keystore.exists()) {
            create("release") {
                storeFile = keystore
                storePassword = localProperties.getProperty("release.storePassword")
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = localProperties.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Observed, not documented: with a Test Store key in a non-debuggable
            // build the SDK puts up a "Wrong API Key" dialog and closes the app. That
            // is the right default, because RevenueCat's own guidance is never to
            // submit a store build configured with a Test Store key.
            //
            // This app is never submitted to a store. It is installed as an APK and
            // every purchase it can make is simulated, so the situation the guard
            // exists to prevent cannot arise. Play also rejects debuggable artifacts,
            // which is the same moment this and the key would both have to change.
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.revenuecat.purchases)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
