package com.quata.core.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevents stale configuration reads from contexts captured by Compose launchers. */
class ComposeConfigurationReadStaticTest {
    private val root = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun composableSourcesUseLocalConfigurationInsteadOfContextResourcesConfiguration() {
        val violations = File(root, "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
            .filter { (_, source) -> "@Composable" in source && "resources.configuration" in source }
            .map { (path, _) -> path }
            .toList()

        assertTrue(
            "Composable sources must derive locale/orientation from LocalConfiguration.current: $violations",
            violations.isEmpty(),
        )
    }
}
