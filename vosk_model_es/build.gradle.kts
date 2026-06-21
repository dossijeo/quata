plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.quata.vosk.model.es"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":app"))
}
