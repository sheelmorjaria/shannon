plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.1.20"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    id("app.cash.sqldelight") version "2.0.2"
}

android {
    namespace = "com.shannon"
    compileSdk = 35  // Use latest stable API

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

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.7.3"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
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
    implementation(compose.uiToolingPreview)

    // SQLDelight Android Driver
    implementation(libs.sqldelight.android.driver)

    // Koin
    implementation(libs.koin.core)
    implementation("io.insert-koin:koin-android:3.5.6")

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Foreground Service support
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(compose.uiTest)
}

sqldelight {
    databases {
        create("ShannonDatabase") {
            packageName.set("com.shannon.db")
        }
    }
}