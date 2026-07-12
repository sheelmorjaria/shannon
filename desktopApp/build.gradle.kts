plugins {
    kotlin("jvm")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// JDK 21 via toolchain: Gradle auto-detects/provisions it on any OS — no org.gradle.java.home needed.
kotlin {
    jvmToolchain(21)
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
    // JavaFX WebView (§4.2 + §4.3): OS-detected classifier — standard Gradle DependencyHandler
    // resolves them (KGP's KotlinDependencyHandler in KMP silently dropped classifiers).
    val osClassifier = when {
        System.getProperty("os.name").lowercase().contains("win") -> "win"
        System.getProperty("os.name").lowercase().contains("mac") -> "mac"
        else -> "linux"
    }
    implementation("org.openjfx:javafx-base:21:$osClassifier")
    implementation("org.openjfx:javafx-graphics:21:$osClassifier")
    implementation("org.openjfx:javafx-controls:21:$osClassifier")
    implementation("org.openjfx:javafx-web:21:$osClassifier")
    implementation("org.openjfx:javafx-media:21:$osClassifier")
    implementation("org.openjfx:javafx-swing:21:$osClassifier")
    // On-device STT + TTS + VAD via Sherpa-ONNX (§2.2; the earlier Vosk tasks were replaced by Sherpa).
    // Model files are provided at runtime (downloaded separately to the modelPath).
    // Non-transitive — the artifact's POM pulls conflicting deps. Coordinate cataloged in §6.1.
    implementation(libs.sherpa.onnx.java.api) { isTransitive = false }

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

// §4.1: bundle the built React UI (UI/dist) into the app's resources so it ships in the JAR.
tasks.register<Copy>("copyWebUi") {
    from(rootProject.layout.projectDirectory.dir("UI/dist"))
    into(layout.buildDirectory.dir("resources/main/web"))
}
tasks.named("processResources") { dependsOn("copyWebUi") }
