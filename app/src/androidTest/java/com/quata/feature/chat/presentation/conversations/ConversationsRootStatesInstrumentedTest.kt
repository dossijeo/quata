package com.quata.feature.chat.presentation.conversations

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
            model.state.value = ConversationsUiState(isLoading = false, error = "forced-conversations-error")
        }
        compose.onAllNodesWithTag(ConversationEmptyTestTag).assertCountEquals(0)
        compose.onNodeWithTag(ConversationErrorTestTag).assertIsDisplayed()
        compose.onNodeWithTag(ConversationRetryTestTag).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(2, model.refreshes) }
    }
}

private class AndroidRootConversationsModel(initial: ConversationsUiState) : ConversationsScreenModel {
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

private object AndroidRootClipboardService : ClipboardService {
    override suspend fun readText(): String? = null
    override suspend fun writeText(text: String) = Unit
}
