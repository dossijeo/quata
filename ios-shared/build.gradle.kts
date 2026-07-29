import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkConfig
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

val privacyManifest = layout.projectDirectory.file("PrivacyInfo.xcprivacy")

tasks.withType<FatFrameworkTask>().configureEach {
    inputs.file(privacyManifest)
    outputs.file(destinationDirProperty.file("QuataShared.framework/PrivacyInfo.xcprivacy"))
    doLast {
        privacyManifest.asFile.copyTo(
            fatFramework.resolve("PrivacyInfo.xcprivacy"),
            overwrite = true,
        )
    }
}

tasks.matching {
    it.name == "assembleQuataSharedDebugXCFramework" ||
        it.name == "assembleQuataSharedReleaseXCFramework"
}.configureEach {
    val buildType = if (name.contains("Release")) "release" else "debug"
    val xcFramework = layout.buildDirectory.dir("XCFrameworks/$buildType/QuataShared.xcframework")
    inputs.file(privacyManifest)
    outputs.dir(xcFramework)
    doLast {
        val frameworks = xcFramework.get().asFile
            .walkTopDown()
            .filter { it.isDirectory && it.name == "QuataShared.framework" }
            .toList()
        check(frameworks.isNotEmpty()) {
            "QuataShared XCFramework contains no framework slices: ${xcFramework.get().asFile}"
        }
        frameworks.forEach { framework ->
            privacyManifest.asFile.copyTo(
                framework.resolve("PrivacyInfo.xcprivacy"),
                overwrite = true,
            )
        }
    }
}

/**
 * The sole Kotlin/Native framework embedded by iosApp.
 *
 * It owns the iOS export boundary only. Feature implementation dependencies
 * stay in their respective modules; this module intentionally contains no app
 * navigation, repositories, or Compose screens.
 */
kotlin {
    // A single binary artifact is required by both the simulator test host and
    // the unsigned generic-device archive.  Keeping the variants together
    // prevents the archive lane from accidentally embedding a simulator-only
    // framework.
    val quataSharedXcFramework = XCFrameworkConfig(project, "QuataShared")

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
            api(project(":feature:profile"))
            api(project(":feature:neighborhoods"))
            api(project(":feature:postcomposer"))
            api(project(":feature:settings"))
            api(project(":feature:whatsnew"))
            api(project(":feature:externalshare"))
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "QuataShared"
            quataSharedXcFramework.add(this)
            export(project(":core"))
            export(project(":feature:auth"))
            export(project(":feature:feed"))
            export(project(":feature:chat"))
            export(project(":feature:notifications"))
            export(project(":feature:official"))
            export(project(":feature:profile"))
            export(project(":feature:neighborhoods"))
            export(project(":feature:postcomposer"))
            export(project(":feature:settings"))
            export(project(":feature:whatsnew"))
            export(project(":feature:externalshare"))
            linkTaskProvider.configure {
                inputs.file(privacyManifest)
                doLast {
                    privacyManifest.asFile.copyTo(
                        outputFile.get().resolve("PrivacyInfo.xcprivacy"),
                        overwrite = true,
                    )
                }
            }
        }
    }
}
