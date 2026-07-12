plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()
    jvm("desktop")
    // JDK 21 via toolchain: Gradle auto-detects/provisions it on any OS, so no
    // org.gradle.java.home is needed (cross-platform WSL + Windows builds).
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.koin.core)
            // Reticulum network library
            implementation(libs.reticulum.kt)
            implementation(libs.lxmf.kt)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.koin.test)
            implementation(libs.sqldelight.jdbc.driver)
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.sqldelight.android.driver)
                implementation("io.insert-koin:koin-android:3.5.6")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.sqldelight.jdbc.driver)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.shannon.shared"
    compileSdk = 36  // rns-android (JitPack reticulum-kt snapshot) requires minCompileSdk 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

// Keep Android-only artifacts (lifecycle/appcompat pulled transitively by koin-android)
// off the desktop (JVM) classpath; the android target still gets its normal Android deps.
configurations.matching { it.name.contains("desktop", ignoreCase = true) }.configureEach {
    exclude(group = "androidx.lifecycle", module = "lifecycle-runtime-ktx")
    exclude(group = "androidx.lifecycle", module = "lifecycle-livedata-core-ktx")
    exclude(group = "androidx.lifecycle", module = "lifecycle-service")
    exclude(group = "androidx.appcompat")
}

configurations.all {
    resolutionStrategy {
        // Force dynamic version resolution for JitPack snapshots
        eachDependency {
            if (requested.group.startsWith("com.github.torlando-tech")) {
                useVersion("main-SNAPSHOT")
            }
        }
        // Cache snapshot versions for shorter time to get latest changes
        cacheDynamicVersionsFor(10, "minutes")
        cacheChangingModulesFor(10, "minutes")
    }
}

// The JitPack 'reticulum-kt' snapshot transitively pulls 'rns-android' (an Android AAR),
// which the desktop (JVM) target cannot consume. Keep Android-only artifacts off JVM
// classpaths so desktopTest/desktopCompile resolve. (Pre-existing snapshot breakage.)
configurations.matching { it.name.contains("desktop", ignoreCase = true) }.configureEach {
    exclude(group = "com.github.torlando-tech.reticulum-kt", module = "rns-android")
}

sqldelight {
    databases {
        create("ShannonDatabase") {
            packageName.set("com.shannon.db")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// commonTest uses desktop-only APIs (JdbcSqliteDriver, runDesktopComposeUiTest) and predates the
// androidTarget — it only ever ran on the jvm("desktop") target. :shared exposes androidMain to
// :androidApp (compileDebugKotlinAndroid), but its tests stay desktop-only (desktopTest/jvmTest,
// which do NOT match "*UnitTest*"). Disable the Android unit-test variant for this module so
// `gradlew test` doesn't try to compile commonTest for Android.
tasks.matching { it.name.contains("UnitTest") }.configureEach { enabled = false }

// KMP's `test` lifecycle task doesn't aggregate the jvm("desktop") target's `desktopTest` in this
// setup, so `./gradlew test` previously skipped :shared's entire suite. Wire it so the root `test`
// runs the desktop target tests.
tasks.matching { it.name == "test" }.configureEach { dependsOn("desktopTest") }
