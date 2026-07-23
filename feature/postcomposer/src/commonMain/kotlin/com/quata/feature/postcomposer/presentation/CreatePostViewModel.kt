package com.quata.feature.postcomposer.presentation

import com.quata.core.common.AppDispatchers
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CreatePostViewModel(
    private val repository: PostComposerRepository,
    dispatchers: AppDispatchers = AppDispatchers()
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()
    private var submitJob: Job? = null

    fun onEvent(event: CreatePostUiEvent) {
        when (event) {
            is CreatePostUiEvent.TextChanged -> _uiState.value = _uiState.value.copy(
                text = event.value,
                error = null,
                successMessage = null
            )
            is CreatePostUiEvent.TextPatternSelected -> _uiState.value = _uiState.value.copy(
                textPatternId = event.patternId,
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
            is CreatePostUiEvent.LocationLabelChanged -> _uiState.value = _uiState.value.copy(
                locationLabel = event.value.takeIf { it.isNotBlank() },
                error = null,
                successMessage = null
            )
            CreatePostUiEvent.ClearDraft -> _uiState.value = CreatePostUiState()
            CreatePostUiEvent.Submit -> submit(PostComposerType.Text)
            CreatePostUiEvent.ClearMessage -> _uiState.value = _uiState.value.copy(successMessage = null, error = null)
        }
    }

    fun submit(type: PostComposerType) {
        if (submitJob?.isActive == true) return
        val state = _uiState.value
        _uiState.value = state.copy(isLoading = true, error = null, successMessage = null)
        submitJob = scope.launch {
            try {
                repository.createPost(
                    PostComposerDraft(
                        type = type,
                        text = state.text,
                        textPatternId = state.textPatternId,
                        imageUri = state.imageUri,
                        videoUri = state.videoUri,
                        locationLabel = state.locationLabel?.trim()?.takeIf { it.isNotBlank() },
                        latitude = state.latitude,
                        longitude = state.longitude
                    )
                )
                    .onSuccess { postId -> _uiState.value = CreatePostUiState(successMessage = "Publicacion creada", createdPostId = postId) }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) {
                            _uiState.value = state.copy(isLoading = false)
                        } else {
                            _uiState.value = state.copy(isLoading = false, error = throwable.message ?: "No se pudo publicar")
                        }
                    }
            } finally {
                submitJob = null
            }
        }
    }

    fun cancelSubmit() {
        submitJob?.cancel()
        submitJob = null
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    fun close() {
        cancelSubmit()
        scope.coroutineContext.cancel()
    }
}
