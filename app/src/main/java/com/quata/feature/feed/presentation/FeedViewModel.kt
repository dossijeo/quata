package com.quata.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FeedViewModel(private val repository: FeedRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init { load() }

    fun onEvent(event: FeedUiEvent) {
        when (event) { FeedUiEvent.Refresh -> load() }
    }

    private fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        repository.getFeed()
            .onSuccess { _uiState.value = FeedUiState(isLoading = false, posts = it) }
            .onFailure { _uiState.value = FeedUiState(isLoading = false, error = it.message ?: "Error cargando feed") }
    }

    companion object {
        fun factory(repository: FeedRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedViewModel(repository) as T
        }
    }
}
