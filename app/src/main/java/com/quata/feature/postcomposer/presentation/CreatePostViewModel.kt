package com.quata.feature.postcomposer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreatePostViewModel(private val repository: PostComposerRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    fun onEvent(event: CreatePostUiEvent) {
        when (event) {
            is CreatePostUiEvent.TextChanged -> _uiState.value = _uiState.value.copy(
                text = event.value,
                error = null,
                successMessage = null
            )
            is CreatePostUiEvent.ImageSelected -> _uiState.value = _uiState.value.copy(
                imageUri = event.uri,
                error = null,
                successMessage = null
            )
            is CreatePostUiEvent.VideoSelected -> _uiState.value = _uiState.value.copy(
                videoUri = event.uri,
                error = null,
                successMessage = null
            )
            is CreatePostUiEvent.LocationResolved -> _uiState.value = _uiState.value.copy(
                locationLabel = event.label,
                latitude = event.latitude,
                longitude = event.longitude,
                error = null,
                successMessage = null
            )
            CreatePostUiEvent.Submit -> submit(PostComposerType.Text)
            CreatePostUiEvent.ClearMessage -> _uiState.value = _uiState.value.copy(successMessage = null, error = null)
        }
    }

    fun submit(type: PostComposerType) = viewModelScope.launch {
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, error = null, successMessage = null)
        repository.createPost(
            PostComposerDraft(
                type = type,
                text = state.text,
                imageUri = state.imageUri,
                videoUri = state.videoUri,
                locationLabel = state.locationLabel,
                latitude = state.latitude,
                longitude = state.longitude
            )
        )
            .onSuccess { _uiState.value = CreatePostUiState(successMessage = "Publicacion creada") }
            .onFailure { _uiState.value = state.copy(isLoading = false, error = it.message ?: "No se pudo publicar") }
    }

    companion object {
        fun factory(repository: PostComposerRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CreatePostViewModel(repository) as T
        }
    }
}
