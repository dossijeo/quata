pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Kotlin/JS registers the official Node.js distribution repository while
    // provisioning its toolchain. Its distribution is not available from Maven,
    // so Gradle must honour that target-specific project repository.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Quata"
include(":app")
include(":document-reader")
include(":vosk_model_en")
include(":vosk_model_es")
include(":vosk_model_fr")

// KMP migration boundary. These modules intentionally remain disconnected from :app
// until their first contents have been migrated and verified on Android.
include(":core")
include(":designsystem")
include(":web")
include(":feature:feed")
include(":feature:chat")
include(":feature:profile")
include(":feature:official")
include(":feature:postcomposer")
include(":feature:settings")
include(":feature:auth")
include(":feature:neighborhoods")
include(":feature:notifications")
include(":feature:whatsnew")
include(":feature:externalshare")
