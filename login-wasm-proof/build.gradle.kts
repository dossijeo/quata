plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core"))
            implementation(project(":designsystem"))
            implementation(project(":feature:auth"))
            implementation(compose.foundation)
            implementation(compose.material3)
        }
    }
}
