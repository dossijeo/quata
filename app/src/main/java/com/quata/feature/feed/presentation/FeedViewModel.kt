package com.quata.feature.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.core.model.User
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
        when (event) {
            FeedUiEvent.Refresh -> load()
            is FeedUiEvent.PostDisplayed -> refreshDisplayedPostAuthor(event.postId)
        }
    }

    private fun load() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        repository.getFeed()
            .onSuccess { _uiState.value = FeedUiState(isLoading = false, posts = it) }
            .onFailure { _uiState.value = FeedUiState(isLoading = false, error = it.message ?: "Error cargando feed") }
    }

    private fun refreshDisplayedPostAuthor(postId: String) = viewModelScope.launch {
        val post = _uiState.value.posts.firstOrNull { it.id == postId } ?: return@launch
        repository.refreshAuthor(post.author.id)
            .onSuccess { author ->
                if (author != null) updatePostAuthor(postId, author)
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message ?: _uiState.value.error)
            }
    }

    private fun updatePostAuthor(postId: String, author: User) {
        _uiState.value = _uiState.value.copy(
            posts = _uiState.value.posts.map { post ->
                if (post.id == postId) post.copy(author = author) else post
            }
        )
    }

    companion object {
        fun factory(repository: FeedRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = FeedViewModel(repository) as T
        }
    }
}
