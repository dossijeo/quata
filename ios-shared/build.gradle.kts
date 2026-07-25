plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

/**
 * The sole Kotlin/Native framework embedded by iosApp.
 *
 * It owns the iOS export boundary only. Feature implementation dependencies
 * stay in their respective modules; this module intentionally contains no app
 * navigation, repositories, or Compose screens.
 */
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // These are API dependencies because the framework exports their
            // Kotlin/Native declarations to Swift. Keep this list constrained
            // to the feature entry points currently composed by iosApp.
            api(project(":core"))
            api(project(":feature:auth"))
            api(project(":feature:feed"))
            api(project(":feature:chat"))
            api(project(":feature:notifications"))
            api(project(":feature:official"))
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "QuataShared"
            export(project(":core"))
            export(project(":feature:auth"))
            export(project(":feature:feed"))
            export(project(":feature:chat"))
            export(project(":feature:notifications"))
            export(project(":feature:official"))
        }
    }
}
