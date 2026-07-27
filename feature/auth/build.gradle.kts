plugins {
    id("quata.kmp-compose-feature")
}

kotlin {
    androidLibrary { namespace = "com.quata.feature.auth"; compileSdk = 36; minSdk = 26 }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":designsystem"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        androidMain.dependencies { }
        iosMain.dependencies { }
        jsMain.dependencies { }
        wasmJsMain.dependencies { }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
