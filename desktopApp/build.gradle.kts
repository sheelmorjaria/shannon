plugins {
    kotlin("jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.websockets)
    // JavaFX WebView (§4.2): standard Gradle DependencyHandler supports classifier notation
    // (KGP's KotlinDependencyHandler in KMP sourceSets silently dropped them).
    // Switch linux→win/mac on other platforms.
    implementation("org.openjfx:javafx-base:21:linux")
    implementation("org.openjfx:javafx-graphics:21:linux")
    implementation("org.openjfx:javafx-controls:21:linux")
    implementation("org.openjfx:javafx-web:21:linux")
    implementation("org.openjfx:javafx-swing:21:linux")

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
}

compose.desktop {
    application {
        mainClass = "com.shannon.MainKt"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
