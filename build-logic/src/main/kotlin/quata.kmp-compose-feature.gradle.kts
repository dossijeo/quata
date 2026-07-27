/**
 * Shared, target-only convention for KMP Compose feature modules.
 *
 * Android remains in each module because its namespace is feature-specific.
 * Dependencies and source-set contents deliberately remain local as well: a
 * convention must not make a feature acquire dependencies it did not have.
 */
plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    useConfigDirectory(rootProject.file("web/karma.config.d"))
                }
            }
        }
        nodejs()
    }
}
