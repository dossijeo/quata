plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidLibrary {
        namespace = "com.quata.core"
        compileSdk = 36
        minSdk = 26
        withHostTestBuilder {
            sourceSetTreeName = "test"
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser()
    }
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-ktx:1.13.0")
            implementation("androidx.core:core-ktx:1.18.0")
            implementation("com.google.android.gms:play-services-location:21.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
            implementation("androidx.media3:media3-exoplayer:1.5.1")
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
        }
        iosMain.dependencies { }
        jsMain.dependencies { }
        wasmJsMain.dependencies { }
    }
}
