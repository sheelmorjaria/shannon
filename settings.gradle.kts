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
include(":androidApp")  // §2.1: re-enabled for Android Sherpa STT (was disabled "for build testing")
