plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    androidLibrary {
        namespace = "com.quata.web"
        compileSdk = 36
        minSdk = 26
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":designsystem"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:feed"))
            implementation(project(":feature:chat"))
            implementation(project(":feature:official"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        androidMain.dependencies { }
        iosMain.dependencies { }
        wasmJsMain.dependencies { }
    }
}
