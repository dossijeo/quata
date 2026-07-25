plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Kept aligned with the root build. This build only supplies convention
    // scripts; it does not own or upgrade any production plugin version.
    implementation("com.android.tools.build:gradle:9.1.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    // `org.jetbrains.kotlin.plugin.compose` is published separately from the
    // base KMP Gradle plugin. A precompiled script convention does not inherit
    // the root build's apply-false plugin classpath.
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.21")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.10.0")
}
