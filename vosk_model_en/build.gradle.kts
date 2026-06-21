plugins {
    id("com.android.dynamic-feature")
}

android {
    namespace = "com.quata.vosk.model.en"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(project(":app"))
}
