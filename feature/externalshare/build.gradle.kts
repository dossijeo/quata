plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    androidLibrary { namespace = "com.quata.feature.externalshare"; compileSdk = 36; minSdk = 26 }
    iosX64(); iosArm64(); iosSimulatorArm64()
    js(IR) { browser() }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":feature:chat"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies { }
        iosMain.dependencies { }
        jsMain.dependencies { }
    }
}
