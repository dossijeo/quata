plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.quata.vosk.model.es"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":app"))
}
