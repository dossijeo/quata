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
            implementation(project(":feature:postcomposer"))
            implementation(project(":feature:notifications"))
            implementation(project(":feature:profile"))
            implementation(project(":feature:neighborhoods"))
            implementation(project(":feature:settings"))
            implementation(project(":feature:whatsnew"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
        }
        androidMain.dependencies { }
        wasmJsMain.dependencies { }
    }
}
