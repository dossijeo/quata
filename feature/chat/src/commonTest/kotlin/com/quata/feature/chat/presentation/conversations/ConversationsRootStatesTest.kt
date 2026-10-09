package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.Conversation
import com.quata.core.platform.ClipboardService
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ConversationsRootStatesTest {
    @Test
    fun rootExposesLoadingEmptyErrorAndRetryThroughTheSharedHost() = runComposeUiTest {
        val model = RootConversationsModel(ConversationsUiState(isLoading = true))
        setContent { ConversationsRootFixture(model) }

        onNodeWithTag(ConversationListTestTag).assertIsDisplayed()
        onAllNodesWithTag(ConversationEmptyTestTag).assertCountEquals(0)
        onAllNodesWithTag(ConversationErrorTestTag).assertCountEquals(0)

        runOnIdle { model.state.value = ConversationsUiState(isLoading = false) }
        onNodeWithTag(ConversationEmptyTestTag).assertIsDisplayed()
        onNodeWithTag(ConversationRetryTestTag).assertIsDisplayed().performClick()
        runOnIdle { assertEquals(1, model.refreshes) }

        runOnIdle {
            model.state.value = ConversationsUiState(isLoading = false, loadError = "forced-conversations-error")
        }
        onAllNodesWithTag(ConversationEmptyTestTag).assertCountEquals(0)
        onNodeWithTag(ConversationErrorTestTag).assertIsDisplayed()
        onNodeWithText("forced-conversations-error").assertIsDisplayed()
        onNodeWithTag(ConversationRetryTestTag).assertIsDisplayed().performClick()
        runOnIdle { assertEquals(2, model.refreshes) }
    }

    @Test
    fun populatedRootKeepsTheConversationInsideTheStableListAnchor() = runComposeUiTest {
        val conversation = Conversation(
            id = "conversation-root-row",
            title = "Conversation root visible",
            lastMessagePreview = "conversation-root-preview",
        )
        val model = RootConversationsModel(
            ConversationsUiState(isLoading = false, conversations = listOf(conversation)),
        )
        setContent { ConversationsRootFixture(model) }

        onNodeWithTag(ConversationListTestTag).assertIsDisplayed()
        onNodeWithTag(conversationRowTestTag(conversation.id)).assertIsDisplayed()
        onNodeWithText("Conversation root visible").assertIsDisplayed()
        onAllNodesWithTag(ConversationEmptyTestTag).assertCountEquals(0)
        onAllNodesWithTag(ConversationErrorTestTag).assertCountEquals(0)
        onAllNodesWithTag(ConversationRetryTestTag).assertCountEquals(0)
    }

    @Test
    fun privateCreateFailureKeepsRenderedPickerAndRetryOpensExactlyOnce() = runComposeUiTest {
        val candidate = renderedCandidate("private-retry-profile")
        val model = RenderedRetryConversationsModel(
            ConversationsUiState(
                isNewConversationPickerOpen = true,
                candidateQuery = "private retained query",
                conversationCandidates = listOf(candidate),
                candidateHasMore = false,
                candidateError = "forced-private-create-error",
            ),
            initialPrivateAttempts = listOf(candidate.profileId),
        )
        val opened = mutableListOf<String>()
        setContent { ConversationsRootFixture(model, onOpenConversation = opened::add) }

        onNodeWithTag(ConversationPickerRootTestTag).assertIsDisplayed()
        onNodeWithTag(ConversationPickerSearchTestTag).assertTextContains("private retained query")
        onNodeWithTag(ConversationPickerCandidateTestTagPrefix + candidate.profileId).assertIsDisplayed()
        onNodeWithTag(ConversationPickerErrorTestTag).assertIsDisplayed()
        onNodeWithText("forced-private-create-error").assertIsDisplayed()
        runOnIdle {
            assertEquals(listOf(candidate.profileId), model.privateAttempts)
            assertEquals(emptyList(), opened)
        }

        onNodeWithTag(ConversationPickerCandidateActionTestTagPrefix + candidate.profileId)
            .assertIsEnabled()
            .performClick()

        onAllNodesWithTag(ConversationPickerRootTestTag).assertCountEquals(0)
        onAllNodesWithTag(ConversationPickerErrorTestTag).assertCountEquals(0)
        runOnIdle {
            assertEquals(listOf(candidate.profileId, candidate.profileId), model.privateAttempts)
            assertEquals(listOf("private-retry-conversation"), opened)
        }
    }

    @Test
    fun groupCreateFailureKeepsRenderedSelectionTitleAndStableRetryKey() = runComposeUiTest {
        val first = renderedCandidate("group-retry-first")
        val second = renderedCandidate("group-retry-second")
        val requestKey = "stable-group-request"
        val model = RenderedRetryConversationsModel(
            ConversationsUiState(
                isNewConversationPickerOpen = true,
                candidateQuery = "group retained query",
                conversationCandidates = listOf(first, second),
                candidateHasMore = false,
                selectedNewConversationProfileIds = setOf(first.profileId, second.profileId),
                newGroupTitle = "Retained group title",
                candidateError = "forced-group-create-error",
            ),
            initialGroupRequestKeys = listOf(requestKey),
            groupRequestKey = requestKey,
        )
        val opened = mutableListOf<String>()
        setContent { ConversationsRootFixture(model, onOpenConversation = opened::add) }

        onNodeWithTag(ConversationPickerRootTestTag).assertIsDisplayed()
        onNodeWithTag(ConversationPickerSearchTestTag).assertTextContains("group retained query")
        onNodeWithTag(ConversationPickerGroupTitleTestTag).assertTextContains("Retained group title")
        onNodeWithTag(ConversationPickerCandidateTestTagPrefix + first.profileId).assertIsDisplayed()
        onNodeWithTag(ConversationPickerCandidateTestTagPrefix + second.profileId).assertIsDisplayed()
        onNodeWithTag(ConversationPickerErrorTestTag).assertIsDisplayed()
        runOnIdle {
            assertEquals(listOf(requestKey), model.groupRequestKeys)
            assertEquals(emptyList(), opened)
        }

        onNodeWithTag(ConversationPickerConfirmTestTag).assertIsEnabled().performClick()

        onAllNodesWithTag(ConversationPickerRootTestTag).assertCountEquals(0)
        onAllNodesWithTag(ConversationPickerErrorTestTag).assertCountEquals(0)
        runOnIdle {
            assertEquals(listOf(requestKey, requestKey), model.groupRequestKeys)
            assertEquals(listOf("group-retry-conversation"), opened)
        }
    }
}

@androidx.compose.runtime.Composable
private fun ConversationsRootFixture(
    model: ConversationsScreenModel,
    onOpenConversation: (String) -> Unit = {},
) {
    QuataTheme {
        ConversationsScreenHost(
            padding = PaddingValues(),
            model = model,
            clipboardService = RootClipboardService,
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

private class RenderedRetryConversationsModel(
    initial: ConversationsUiState,
    initialPrivateAttempts: List<String> = emptyList(),
    initialGroupRequestKeys: List<String> = emptyList(),
    private val groupRequestKey: String = "stable-group-request",
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
        onOpened("private-retry-conversation")
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
        onOpened("group-retry-conversation")
    }
    override fun close() = Unit
}

private fun renderedCandidate(profileId: String) = ChatConversationCandidate(
    profileId = profileId,
    displayName = profileId,
    neighborhood = "Rendered retry",
    phone = "",
    avatarUrl = null,
    sectionKey = "following",
    neighborhoodGroup = "",
    existingConversationId = null,
)

private class RootConversationsModel(initial: ConversationsUiState) : ConversationsScreenModel {
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

private object RootClipboardService : ClipboardService {
    override suspend fun readText(): String? = null
    override suspend fun writeText(text: String) = Unit
}
