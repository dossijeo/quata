package com.quata.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for the shared FeedViewModel. */
class FeedAndroidViewModel(repository: FeedRepository) : ViewModel(), FeedStateHolder {
    private val delegate = FeedViewModel(repository)
    override val uiState: StateFlow<FeedUiState> = delegate.uiState

    override fun onEvent(event: FeedUiEvent) = delegate.onEvent(event)

    override fun onCleared() {
        delegate.close()
    }

    companion object {
        fun factory(repository: FeedRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedAndroidViewModel(repository) as T
        }
    }
}
