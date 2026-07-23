package com.quata.feature.official.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.flow.StateFlow

/** Android lifecycle adapter for shared official-feed presentation logic. */
class OfficialFeedAndroidViewModel(repository: OfficialRepository) : ViewModel() {
    private val delegate = OfficialFeedViewModel(repository)
    val uiState: StateFlow<OfficialFeedUiState> = delegate.uiState
    fun onEvent(event: OfficialFeedUiEvent) = delegate.onEvent(event)
    fun refreshCurrentUser() = delegate.refreshCurrentUser()

    override fun onCleared() = delegate.close()

    companion object {
        fun factory(repository: OfficialRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OfficialFeedAndroidViewModel(repository) as T
        }
    }
}
