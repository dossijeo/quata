plugins {
    id("quata.kmp-compose-feature")
}

kotlin {
    androidLibrary { namespace = "com.quata.feature.settings"; compileSdk = 36; minSdk = 26 }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":designsystem"))
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        androidMain.dependencies { }
        iosMain.dependencies { }
        wasmJsMain.dependencies { }
    }
}
