plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidLibrary { namespace = "com.quata.feature.neighborhoods"; compileSdk = 36; minSdk = 26 }
    iosX64(); iosArm64(); iosSimulatorArm64()
    wasmJs { browser(); nodejs() }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":designsystem"))
            implementation(project(":feature:chat"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        androidMain.dependencies { }
        // The iOS Communities adapter opens only through the established Chat domain contract.
        // Keeping this dependency platform-scoped avoids coupling the shared Communities domain
        // or presentation code to Chat while allowing the UIKit composition root to reuse its
        // authenticated repository.
        iosMain.dependencies { implementation(project(":feature:feed")) }
        wasmJsMain.dependencies { }
    }
}
