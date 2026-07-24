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
        }
        androidMain.dependencies { }
        iosMain.dependencies { }
        jsMain.dependencies { }
        wasmJsMain.dependencies { }
    }
}
