plugins {
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.koin.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.server.core)
                implementation(libs.ktor.server.websockets)
                implementation(libs.ktor.server.cio)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.shannon.MainKt"
    }
}
