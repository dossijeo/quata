package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.click
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfilePrivateChatRenderedRaceTest {
    @Test
    fun firstRenderedChatActionDisablesItselfAndItsSiblingBeforeAnotherTapCanOpen() = runComposeUiTest {
        val opens = mutableListOf<String>()
        val users = listOf(
            NeighborhoodUser("profile-a", "Ada", "", "Centro"),
            NeighborhoodUser("profile-b", "Biko", "", "Centro"),
        )

        setContent {
            QuataTheme {
                var openingUserId by remember { mutableStateOf<String?>(null) }
                ProfileUsersListCommon(
                    listKind = "followers",
                    title = "Followers",
                    users = users,
                    currentUserId = "current-user",
                    isOpeningChat = openingUserId != null,
                    openingPrivateChatUserId = openingUserId,
                    openingProfileUserId = null,
                    followingUserId = null,
                    strings = NeighborhoodUserRowStrings("Follow", "Following", "Chat"),
                    back = "Back",
                    avatar = { _, _, modifier, _ -> Box(modifier) },
                    onBack = {},
                    onFollow = {},
                    onProfile = {},
                    onChat = { user ->
                        opens += user.id
                        if (openingUserId == null) {
                            openingUserId = user.id
                        }
                    },
                )
            }
        }

        val first = onNodeWithTag(PublicProfileUserListChatActionTestTagPrefix + "followers.profile-a")
        val sibling = onNodeWithTag(PublicProfileUserListChatActionTestTagPrefix + "followers.profile-b")
        first.assertIsEnabled()
        sibling.assertIsEnabled()

        first.performTouchInput { click() }
        waitForIdle()
        first.assertIsNotEnabled()
        sibling.assertIsNotEnabled()
        onNodeWithTag(PublicProfileUserListChatProgressTestTagPrefix + "followers.profile-a")
            .assertIsDisplayed()
        onNodeWithTag(PublicProfileUserListChatProgressTestTagPrefix + "followers.profile-b")
            .assertDoesNotExist()

        first.performTouchInput { click() }
        sibling.performTouchInput { click() }
        runOnIdle { assertEquals(listOf("profile-a"), opens) }
    }
}
