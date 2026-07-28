package com.quata.feature.feed.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MigrationStatusSemanticsTest {
    @Test
    fun actionChangesTheRealComposeStatusSemantics() = runComposeUiTest {
        var acknowledged by mutableStateOf(false)
        setContent {
            QuataTheme {
                FeedStatusContent(
                    message = if (acknowledged) "updated" else "initial",
                    retryLabel = "Acknowledge",
                    onRetry = { acknowledged = true },
                    messageTag = "migration-message",
                    actionTag = "migration-action",
                )
            }
        }
        onNodeWithTag("migration-message").assertTextEquals("initial")
        onNodeWithTag("migration-action").performClick()
        onNodeWithTag("migration-message").assertTextEquals("updated")
    }
}
