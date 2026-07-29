@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val webSourceRevision = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }
val webDistributionRevision = layout.buildDirectory.file(
    "dist/wasmJs/productionExecutable/quata-source-revision.txt",
)

kotlin {
    androidLibrary {
        namespace = "com.quata.web"
        compileSdk = 36
        minSdk = 26
    }
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    useConfigDirectory(rootProject.file("web/karma.config.d"))
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":designsystem"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:feed"))
            implementation(project(":feature:chat"))
            implementation(project(":feature:externalshare"))
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
            implementation(compose.runtimeSaveable)
        }
        wasmJsTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.uiTest)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        androidMain.dependencies { }
        wasmJsMain.dependencies {
            // Web-only dynamic import. Keep DocMentis and its WASM runtime out of commonMain so
            // Android/iOS never resolve a browser package.
            implementation(npm("@docmentis/udoc-viewer", "0.7.9"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// A normal Web verification must retain the real browser semantics gate.
tasks.named("check") {
    dependsOn("wasmJsBrowserTest")
}

// Bind the emitted production distribution to the exact committed source revision. The
// authenticated browser gate rejects a missing/stale marker and a dirty tracked source tree.
tasks.named("wasmJsBrowserDistribution") {
    inputs.property("quataSourceRevision", webSourceRevision)
    outputs.file(webDistributionRevision)
    doLast {
        webDistributionRevision.get().asFile.apply {
            parentFile.mkdirs()
            writeText("${webSourceRevision.get()}\n")
        }
    }
}
