plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm("desktop")

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
