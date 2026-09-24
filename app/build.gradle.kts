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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    namespace = "dev.kesav.redline"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.kesav.redline"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        // The text recognition model is native code, about 11 MB per ABI, and every ABI
        // included is paid for by every download of the one APK on the Release. 32-bit
        // x86 only ever ran on old emulators; 32-bit ARM stays, because it is still what
        // the cheapest phones run, and they are the ones a tenant is likely to own.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

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

    // A Test Store key is simulated purchases against no store account at all. The SDK
    // refuses to use one outside a debuggable build, so the build type has to follow the
    // key rather than the other way round. See the release block below.
    val usingTestStore = localProperties
        .getProperty("revenuecat.apiKey", "")
        .startsWith("test_")

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
            // build the SDK puts up a "Wrong API Key" dialog and closes the app. The
            // check is `ApplicationInfo.flags and FLAG_DEBUGGABLE`, reached from
            // PurchasesFactory. It is the right default, because RevenueCat's guidance
            // is never to ship a store build configured against the Test Store.
            //
            // So the two are tied together here rather than one being forced open. A
            // Test Store key produces a debuggable build and a store key does not, and
            // swapping the key is the single edit that flips both. Leaving a bare
            // `isDebuggable = true` behind would have been a workaround that outlived
            // its reason and silently shipped an unoptimised, Play-rejected artifact.
            //
            // Worth stating plainly: a debuggable build disables R8's optimisation and
            // obfuscation passes, so `isMinifyEnabled` above does nothing while a Test
            // Store key is in use. It is kept, with the ProGuard rules, because it is
            // what runs the moment a real store key is configured.
            isDebuggable = usingTestStore
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
    implementation(libs.mlkit.text.recognition)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)

    // Screens rendered on the JVM, so a layout can be checked without a device. Test-only:
    // none of this reaches the APK. Images are written only with -Pshots.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.withType<Test>().configureEach {
    if (project.hasProperty("shots")) systemProperty("roborazzi.test.record", "true")
}
