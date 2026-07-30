import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

@OptIn(ExperimentalComposeLibrary::class)
kotlin {
    androidLibrary { namespace = "com.quata.feature.feed"; compileSdk = 36; minSdk = 26 }
    iosX64(); iosArm64(); iosSimulatorArm64()
    wasmJs { browser(); nodejs() }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(project(":designsystem"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.uiTest)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        androidMain.dependencies { }
        iosMain.dependencies { }
        wasmJsMain.dependencies { }
    }
}
