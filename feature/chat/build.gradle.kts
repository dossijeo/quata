plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidLibrary { namespace = "com.quata.feature.chat"; compileSdk = 36; minSdk = 26 }
    iosX64(); iosArm64(); iosSimulatorArm64()
    js(IR) { browser() }
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":designsystem"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
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
