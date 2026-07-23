package com.quata.feature.chat.presentation.conversations

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.R
import com.quata.feature.chat.data.AndroidContactsReader
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chat.ChatText
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle, contacts and resources adapter for shared conversations logic. */
class ConversationsAndroidViewModel(repository: ChatRepository, context: Context) : ViewModel() {
    private val delegate = ConversationsViewModel(
        repository = repository,
        readContacts = AndroidContactsReader(context).let { reader -> reader::readContacts },
        text = context.applicationContext::conversationText
    )

    val uiState: StateFlow<ConversationsUiState> = delegate.uiState
    fun onEvent(event: ConversationsUiEvent) = delegate.onEvent(event)
    fun openNewConversationPicker() = delegate.openNewConversationPicker()
    fun closeNewConversationPicker() = delegate.closeNewConversationPicker()
    fun onCandidateQueryChanged(query: String) = delegate.onCandidateQueryChanged(query)
    fun loadMoreConversationCandidates() = delegate.loadMoreConversationCandidates()
    fun loadInviteContacts() = delegate.loadInviteContacts()
    fun openCandidateConversation(candidate: com.quata.feature.chat.domain.ChatConversationCandidate, onOpened: (String) -> Unit) =
        delegate.openCandidateConversation(candidate, onOpened)

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: ChatRepository, context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ConversationsAndroidViewModel(repository, context) as T
        }
    }
}

private fun Context.conversationText(value: ChatText): String = getString(
    when (value) {
        ChatText.LoadCandidates -> R.string.chat_error_load_candidates
        ChatText.OpenConversation -> R.string.chat_error_open_conversation
        ChatText.LoadConversations -> R.string.chat_error_load_conversations
        ChatText.RestoreConversation -> R.string.chat_error_restore_conversation
        ChatText.DeleteConversation -> R.string.chat_error_delete_conversation
        else -> R.string.chat_error_load_conversations
    }
)
