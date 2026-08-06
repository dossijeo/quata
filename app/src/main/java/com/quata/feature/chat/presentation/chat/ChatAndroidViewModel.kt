package com.quata.feature.chat.presentation.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.R
import com.quata.core.navigation.AppDestinations
import com.quata.feature.chat.domain.ChatRepository
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle and resource adapter for the shared ChatViewModel. */
class ChatAndroidViewModel(
    conversationId: String,
    repository: ChatRepository,
    context: Context
) : ViewModel() {
    internal val commonModel = ChatViewModel(
        conversationId = conversationId,
        repository = repository,
        isFavoritesConversation = conversationId == AppDestinations.FavoriteMessagesConversationId,
        text = context.applicationContext::androidChatText
    )

    val uiState: StateFlow<ChatUiState> = commonModel.uiState
    fun onEvent(event: ChatUiEvent) = commonModel.onEvent(event)
    fun setConversationVisible(visible: Boolean) = commonModel.setConversationVisible(visible)
    fun cleanupEmptyConversationIfNeeded() = commonModel.cleanupEmptyConversationIfNeeded()
    fun loadOlderMessages() = commonModel.loadOlderMessages()
    fun retryPendingMessage(clientMessageId: String) = commonModel.retryPendingMessage(clientMessageId)
    fun addConversationCandidateParticipant(profileId: String) = commonModel.addConversationCandidateParticipant(profileId)
    fun onParticipantCandidateQueryChanged(query: String) = commonModel.onParticipantCandidateQueryChanged(query)
    fun loadMoreParticipantCandidates() = commonModel.loadMoreParticipantCandidates()
    fun onForwardCandidateQueryChanged(query: String) = commonModel.onForwardCandidateQueryChanged(query)
    fun loadMoreForwardConversationCandidates() = commonModel.loadMoreForwardConversationCandidates()

    override fun onCleared() = commonModel.close()

    companion object {
        fun factory(conversationId: String, repository: ChatRepository, context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatAndroidViewModel(conversationId, repository, context) as T
        }
    }
}

internal fun Context.androidChatText(value: ChatText): String = getString(
    when (value) {
        ChatText.LoadConversations -> R.string.chat_error_load_conversations
        ChatText.LoadMessages -> R.string.chat_error_load_messages
        ChatText.Send -> R.string.chat_error_send
        ChatText.You -> R.string.common_you
        ChatText.Update -> R.string.chat_error_update
        ChatText.AddParticipant -> R.string.chat_error_add_participant
        ChatText.AddParticipants -> R.string.chat_error_add_participants
        ChatText.LoadCandidates -> R.string.chat_error_load_candidates
        ChatText.DeleteConversation -> R.string.chat_error_delete_conversation
        ChatText.LeaveConversation -> R.string.chat_error_leave_conversation
        ChatText.PromoteParticipant -> R.string.chat_error_promote_participant
        ChatText.DemoteParticipant -> R.string.chat_error_demote_participant
        ChatText.RemoveParticipant -> R.string.chat_error_remove_participant
        ChatText.BlockParticipant -> R.string.chat_error_block_participant
        ChatText.UpdateFavorite -> R.string.chat_error_update_favorite
        ChatText.DeleteMessage -> R.string.chat_error_delete_message
        ChatText.ReportSent -> R.string.moderation_report_sent
        ChatText.ReportMessage -> R.string.chat_error_report_message
        ChatText.Forward -> R.string.chat_error_forward
        ChatText.RestoreConversation -> R.string.chat_error_restore_conversation
        ChatText.OpenConversation -> R.string.chat_error_open_conversation
    }
)
