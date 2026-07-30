@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Kotlin configures release webpack builds with `source-map` by default. Those maps are
// debugging artifacts and should not be part of the browser distribution we publish.
// Keep development/test webpack defaults intact by changing only the production task.
tasks.withType<KotlinWebpack>().configureEach {
    if (name == "wasmJsBrowserProductionWebpack") {
        sourceMaps = false
    }
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

// Keep the published production directory free of source-map artifacts. This runs after
// packaging, so it also covers any map a future webpack fragment or copied resource emits.
tasks.register("verifyWasmJsProductionDistributionNoSourceMaps") {
    dependsOn("wasmJsBrowserDistribution")
    inputs.dir(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    doLast {
        val distribution = layout.buildDirectory.dir("dist/wasmJs/productionExecutable").get().asFile
        val sourceMaps = distribution.walkTopDown()
            .filter { it.isFile && it.extension.equals("map", ignoreCase = true) }
            .map { it.relativeTo(distribution).invariantSeparatorsPath }
            .toList()
        check(sourceMaps.isEmpty()) {
            "Production Wasm distribution must not contain source maps: ${sourceMaps.joinToString()}"
        }
    }
}
