package com.quata.feature.externalshare

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.R
import com.quata.core.navigation.AppDestinations
import com.quata.core.text.localizedChatPreview
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chatDisplayTitle
import kotlinx.coroutines.flow.StateFlow

/** Android adapter for lifecycle, resources and conversation presentation details. */
class ShareToQuataAndroidViewModel(
    repository: ChatRepository,
    payload: ExternalSharePayload,
    context: Context
) : ViewModel() {
    private val delegate = ShareToQuataViewModel(
        repository = repository,
        payload = payload,
        text = context.applicationContext::shareText,
        resolvePreview = context.applicationContext::localizedChatPreview,
        conversationTitle = { it.chatDisplayTitle() },
        isFavoriteConversation = { it == AppDestinations.FavoriteMessagesConversationId }
    )
    val uiState: StateFlow<ShareToQuataUiState> = delegate.uiState
    fun onQueryChanged(query: String) = delegate.onQueryChanged(query)
    fun loadMore() = delegate.loadMore()
    fun toggle(profileId: String) = delegate.toggle(profileId)
    fun send() = delegate.send()

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: ChatRepository, payload: ExternalSharePayload, context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ShareToQuataAndroidViewModel(repository, payload, context) as T
        }
    }
}

private fun Context.shareText(value: ShareText): String = getString(
    when (value) {
        ShareText.SendError -> R.string.share_to_quata_send_error
        ShareText.LoadCandidates -> R.string.chat_error_load_candidates
    }
)
