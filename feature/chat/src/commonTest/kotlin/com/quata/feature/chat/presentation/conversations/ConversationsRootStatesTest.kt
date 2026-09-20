package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
}

@androidx.compose.runtime.Composable
private fun ConversationsRootFixture(model: RootConversationsModel) {
    QuataTheme {
        ConversationsScreenHost(
            padding = PaddingValues(),
            model = model,
            clipboardService = RootClipboardService,
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

private class RootConversationsModel(initial: ConversationsUiState) : ConversationsScreenModel {
    val state = MutableStateFlow(initial)
    var refreshes = 0
    override val uiState = state
    override fun onEvent(event: ConversationsUiEvent) {
        if (event == ConversationsUiEvent.Refresh) refreshes += 1
    }
    override fun openNewConversationPicker() = Unit
    override fun closeNewConversationPicker() = Unit
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
