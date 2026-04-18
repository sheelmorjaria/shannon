rootProject.name = "shannon"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

include(":shared")
include(":desktopApp")
// include(":androidApp") // Temporarily disabled for build testing
