plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

android {
    namespace = "com.shannon"
    compileSdk = 36  // rns-android (JitPack reticulum-kt snapshot) requires minCompileSdk 36

    defaultConfig {
        applicationId = "com.shannon"
        minSdk = 26  // Android 8.0 (API 26) - minimum for reticulum-kt
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    // jvmTarget is derived from the Kotlin toolchain (kotlin { jvmToolchain(21) } below);
    // the kotlinOptions DSL was removed in Kotlin 2.x.

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.7.3"
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
                "/META-INF/NOTICE.md",
                "/META-INF/NOTICE.txt",
                "/META-INF/*.kotlin_module",
            )
        }
    }
}

// JDK 21 via toolchain: Gradle auto-detects/provisions it on any OS — no org.gradle.java.home needed.
// (compileOptions / kotlinOptions above already pin 21; toolchain also lets Gradle provision the JDK.
//  NOTE: :androidApp is currently disabled in settings.gradle.kts; verify AGP+toolchain interaction
//  when re-enabling — see §2.1 / add-live-translation-stt-tts.)
kotlin {
    jvmToolchain(21)
}

dependencies {
    // Shared module
    implementation(project(":shared"))

    // AndroidX Core
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.ui)
    // compose.uiToolingPreview is not a Compose-Multiplatform accessor (1.7.3); add the
    // AndroidX ui-tooling-preview artifact if/when @Preview composables are introduced.

    // SQLDelight Android Driver
    implementation(libs.sqldelight.android.driver)

    // Koin
    implementation(libs.koin.core)
    implementation("io.insert-koin:koin-android:3.5.6")

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // On-device STT + TTS + VAD via Sherpa-ONNX (§2.1). Java API jar (non-transitive); the native
    // .so libs are packaged separately in jniLibs at runtime and are not needed for compilation.
    implementation(libs.sherpa.onnx.java.api) { isTransitive = false }

    // Foreground Service support
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    androidTestImplementation(compose.uiTest)
}

sqldelight {
    databases {
        create("ShannonDatabase") {
            packageName.set("com.shannon.db")
        }
    }
}