package com.quata.feature.chat.presentation.conversations

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.platform.ClipboardService
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationsRootStatesInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun conversationsRootExposesLoadingEmptyErrorAndRetry() {
        val model = AndroidRootConversationsModel(ConversationsUiState(isLoading = true))
        compose.setContent {
            QuataTheme {
                ConversationsScreenHost(
                    padding = PaddingValues(),
                    model = model,
                    clipboardService = AndroidRootClipboardService,
                    strings = conversationsHostStringsForLanguage("en"),
                    onOpenConversation = {},
                    remoteConversationAvatar = { _, _ -> },
                    candidateAvatar = { _, _ -> },
                    inviteAvatar = { _, _ -> },
                    panelHost = { _ -> },
                    nowMillisProvider = { 0L },
                    modifier = Modifier,
                )
            }
        }

        compose.onNodeWithTag(ConversationListTestTag).assertIsDisplayed()
        compose.onAllNodesWithTag(ConversationEmptyTestTag).assertCountEquals(0)
        compose.onAllNodesWithTag(ConversationErrorTestTag).assertCountEquals(0)

        compose.runOnIdle { model.state.value = ConversationsUiState(isLoading = false) }
        compose.onNodeWithTag(ConversationEmptyTestTag).assertIsDisplayed()
        compose.onNodeWithTag(ConversationRetryTestTag).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, model.refreshes) }

        compose.runOnIdle {
            model.state.value = ConversationsUiState(isLoading = false, loadError = "forced-conversations-error")
        }
        compose.onAllNodesWithTag(ConversationEmptyTestTag).assertCountEquals(0)
        compose.onNodeWithTag(ConversationErrorTestTag).assertIsDisplayed()
        compose.onNodeWithTag(ConversationRetryTestTag).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, model.refreshes) }
    }

    @Test
    fun privateConversationCreateFailureRendersRetainedRetryAndOpensOnce() {
        val candidate = androidRenderedCandidate("android-private-retry")
        val model = AndroidRenderedRetryConversationsModel(
            ConversationsUiState(
                isNewConversationPickerOpen = true,
                candidateQuery = "android retained query",
                conversationCandidates = listOf(candidate),
                candidateHasMore = false,
                candidateError = "android-private-create-error",
            ),
            initialPrivateAttempts = listOf(candidate.profileId),
        )
        val opened = mutableListOf<String>()
        compose.setContent { AndroidConversationsRootFixture(model, opened::add) }

        compose.onNodeWithTag(ConversationPickerRootTestTag).assertIsDisplayed()
        compose.onNodeWithTag(ConversationPickerSearchTestTag).assertTextContains("android retained query")
        compose.onNodeWithTag(ConversationPickerCandidateTestTagPrefix + candidate.profileId).assertIsDisplayed()
        compose.onNodeWithTag(ConversationPickerErrorTestTag).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(candidate.profileId), model.privateAttempts)
            assertEquals(emptyList<String>(), opened)
        }

        compose.onNodeWithTag(ConversationPickerCandidateActionTestTagPrefix + candidate.profileId)
            .assertIsEnabled()
            .performClick()

        compose.onAllNodesWithTag(ConversationPickerRootTestTag).assertCountEquals(0)
        compose.onAllNodesWithTag(ConversationPickerErrorTestTag).assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf(candidate.profileId, candidate.profileId), model.privateAttempts)
            assertEquals(listOf("android-private-retry-conversation"), opened)
        }
    }

    @Test
    fun groupConversationCreateFailureRendersDraftAndReusesStableKey() {
        val first = androidRenderedCandidate("android-group-first")
        val second = androidRenderedCandidate("android-group-second")
        val requestKey = "android-stable-group-request"
        val model = AndroidRenderedRetryConversationsModel(
            ConversationsUiState(
                isNewConversationPickerOpen = true,
                candidateQuery = "android group query",
                conversationCandidates = listOf(first, second),
                candidateHasMore = false,
                selectedNewConversationProfileIds = setOf(first.profileId, second.profileId),
                newGroupTitle = "Android retained group",
                candidateError = "android-group-create-error",
            ),
            initialGroupRequestKeys = listOf(requestKey),
            groupRequestKey = requestKey,
        )
        val opened = mutableListOf<String>()
        compose.setContent { AndroidConversationsRootFixture(model, opened::add) }

        compose.onNodeWithTag(ConversationPickerRootTestTag).assertIsDisplayed()
        compose.onNodeWithTag(ConversationPickerGroupTitleTestTag).assertTextContains("Android retained group")
        compose.onNodeWithTag(ConversationPickerErrorTestTag).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(requestKey), model.groupRequestKeys)
            assertEquals(emptyList<String>(), opened)
        }

        compose.onNodeWithTag(ConversationPickerConfirmTestTag).assertIsEnabled().performClick()

        compose.onAllNodesWithTag(ConversationPickerRootTestTag).assertCountEquals(0)
        compose.onAllNodesWithTag(ConversationPickerErrorTestTag).assertCountEquals(0)
        compose.runOnIdle {
            assertEquals(listOf(requestKey, requestKey), model.groupRequestKeys)
            assertEquals(listOf("android-group-retry-conversation"), opened)
        }
    }
}

@androidx.compose.runtime.Composable
private fun AndroidConversationsRootFixture(
    model: ConversationsScreenModel,
    onOpenConversation: (String) -> Unit,
) {
    QuataTheme {
        ConversationsScreenHost(
            padding = PaddingValues(),
            model = model,
            clipboardService = AndroidRootClipboardService,
            strings = conversationsHostStringsForLanguage("en"),
            onOpenConversation = onOpenConversation,
            remoteConversationAvatar = { _, _ -> },
            candidateAvatar = { _, _ -> },
            inviteAvatar = { _, _ -> },
            panelHost = { content -> content(Modifier, false) },
            nowMillisProvider = { 0L },
            modifier = Modifier,
        )
    }
}

private class AndroidRenderedRetryConversationsModel(
    initial: ConversationsUiState,
    initialPrivateAttempts: List<String> = emptyList(),
    initialGroupRequestKeys: List<String> = emptyList(),
    private val groupRequestKey: String = "android-stable-group-request",
) : ConversationsScreenModel {
    val state = MutableStateFlow(initial)
    val privateAttempts = initialPrivateAttempts.toMutableList()
    val groupRequestKeys = initialGroupRequestKeys.toMutableList()
    override val uiState = state
    override fun onEvent(event: ConversationsUiEvent) = Unit
    override fun openNewConversationPicker() = Unit
    override fun closeNewConversationPicker() = Unit
    override fun onConversationQueryChanged(query: String) = Unit
    override fun onCandidateQueryChanged(query: String) = Unit
    override fun loadMoreConversationCandidates() = Unit
    override fun loadInviteContacts(contacts: List<ChatInviteContact>?) = Unit
    override fun openCandidateConversation(candidate: ChatConversationCandidate, onOpened: (String) -> Unit) {
        privateAttempts += candidate.profileId
        state.value = state.value.copy(isNewConversationPickerOpen = false, candidateError = null)
        onOpened("android-private-retry-conversation")
    }
    override fun toggleNewConversationCandidate(candidate: ChatConversationCandidate) = Unit
    override fun onNewGroupTitleChanged(title: String) = Unit
    override fun openSelectedGroupConversation(onOpened: (String) -> Unit) {
        groupRequestKeys += groupRequestKey
        state.value = state.value.copy(
            isNewConversationPickerOpen = false,
            selectedNewConversationProfileIds = emptySet(),
            newGroupTitle = "",
            candidateError = null,
        )
        onOpened("android-group-retry-conversation")
    }
    override fun close() = Unit
}

private fun androidRenderedCandidate(profileId: String) = ChatConversationCandidate(
    profileId = profileId,
    displayName = profileId,
    neighborhood = "Rendered retry",
    phone = "",
    avatarUrl = null,
    sectionKey = "following",
    neighborhoodGroup = "",
    existingConversationId = null,
)

private class AndroidRootConversationsModel(initial: ConversationsUiState) : ConversationsScreenModel {
    val state = MutableStateFlow(initial)
    var refreshes = 0
    override val uiState = state
    override fun onEvent(event: ConversationsUiEvent) {
        if (event == ConversationsUiEvent.Refresh) refreshes += 1
    }
    override fun openNewConversationPicker() = Unit
    override fun closeNewConversationPicker() = Unit
    override fun onConversationQueryChanged(query: String) = Unit
    override fun onCandidateQueryChanged(query: String) = Unit
    override fun loadMoreConversationCandidates() = Unit
    override fun loadInviteContacts(contacts: List<ChatInviteContact>?) = Unit
    override fun openCandidateConversation(candidate: ChatConversationCandidate, onOpened: (String) -> Unit) = Unit
    override fun toggleNewConversationCandidate(candidate: ChatConversationCandidate) = Unit
    override fun onNewGroupTitleChanged(title: String) = Unit
    override fun openSelectedGroupConversation(onOpened: (String) -> Unit) = Unit
    override fun close() = Unit
}

private object AndroidRootClipboardService : ClipboardService {
    override suspend fun readText(): String? = null
    override suspend fun writeText(text: String) = Unit
}
