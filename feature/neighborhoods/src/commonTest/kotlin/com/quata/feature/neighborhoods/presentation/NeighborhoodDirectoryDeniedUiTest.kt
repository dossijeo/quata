package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NeighborhoodDirectoryDeniedUiTest {
    @Test
    fun authorizationDenialIsVisibleAndRetriesExactlyOnce() = runComposeUiTest {
        var retries = 0
        val strings = NeighborhoodListStrings(
            title = "Communities",
            searchPlaceholder = "Search",
            loading = "Loading",
            empty = "Empty",
            noResults = "No results",
            oneUser = "1 user",
            users = { "$it users" },
            oneMessage = "1 message",
            messages = { "$it messages" },
            viewUsers = "View users",
            openChat = "Open chat",
            timeLabel = { "Now" },
            directoryAccessDenied = "Directory access denied",
            retry = "Retry directory",
        )

        setContent {
            QuataTheme {
                NeighborhoodListContent(
                    padding = PaddingValues(),
                    communities = emptyList(),
                    query = "",
                    isLoading = false,
                    error = "neighborhood_directory_access_denied",
                    directoryLoadFailed = true,
                    directoryAccessDenied = true,
                    currentUserId = null,
                    openingNeighborhood = null,
                    chatErrorNeighborhood = null,
                    strings = strings,
                    onQueryChange = {},
                    onRetry = { retries += 1 },
                    onShowUsers = {},
                    onOpenChat = {},
                )
            }
        }

        onNodeWithTag(NeighborhoodDirectoryRootTestTag).assertIsDisplayed()
        onNodeWithTag(NeighborhoodDirectoryErrorTestTag).assertIsDisplayed()
        onNodeWithText("Directory access denied").assertIsDisplayed()
        onNodeWithTag(NeighborhoodDirectoryRetryTestTag).assertIsDisplayed().performClick()
        runOnIdle { assertEquals(1, retries) }
    }
}
