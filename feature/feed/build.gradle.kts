plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidLibrary { namespace = "com.quata.feature.feed"; compileSdk = 36; minSdk = 26 }
    iosX64(); iosArm64(); iosSimulatorArm64()
    js(IR) { browser() }
    wasmJs { browser() }
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "QuataFeed"
            // Keep the Swift launcher to one embedded framework while exposing the iOS platform
            // service types needed by the eventual authenticated composition root.
            export(project(":core"))
            // The UIKit launcher embeds a single framework. Re-export Auth only for iOS so its
            // shared Compose login and iOS transport are available without a Swift duplicate.
            export(project(":feature:auth"))
            // Chat's UIKit host is likewise exposed through the sole embedded framework. The
            // Swift router decides when real chat dependencies are available; it does not own
            // a duplicate Chat UI or audio implementation.
            export(project(":feature:chat"))
            // The authenticated UIKit router also installs the shared in-app notifications
            // host through this single framework; keep its KMP entry point visible to Swift.
            export(project(":feature:notifications"))
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(project(":designsystem"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        androidMain.dependencies { }
        iosMain.dependencies {
            api(project(":feature:auth"))
            api(project(":feature:chat"))
            api(project(":feature:notifications"))
        }
        jsMain.dependencies { }
        wasmJsMain.dependencies { }
    }
}
