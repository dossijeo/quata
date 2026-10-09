package com.quata.feature.postcomposer.presentation

import com.quata.core.common.AppDispatchers
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerAuthenticationRequiredException
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CreatePostViewModel(
    private val repository: PostComposerRepository,
    dispatchers: AppDispatchers = AppDispatchers(),
    private val messages: CreatePostMessages = SpanishCreatePostRootCopy.viewModelMessages(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()
    private var submitJob: Job? = null
    private var draftMutationRevision: Long = 0

    init {
        loadDestinations()
    }

    fun onEvent(event: CreatePostUiEvent) {
        when (event) {
            is CreatePostUiEvent.TextChanged -> mutateDraft {
                _uiState.value = _uiState.value.copy(
                    text = event.value.take(CreatePostTextLimit),
                    error = null,
                    lastFailedSubmitType = null,
                    successMessage = null,
                )
            }
            is CreatePostUiEvent.TextPatternSelected -> mutateDraft {
                _uiState.value = _uiState.value.copy(
                    textPatternId = event.patternId,
                    error = null,
                    lastFailedSubmitType = null,
                    successMessage = null,
                )
            }
            is CreatePostUiEvent.DestinationSelected -> mutateDraft {
                _uiState.value = _uiState.value.copy(
                    selectedDestinationWallId = event.wallId.takeIf(String::isNotBlank),
                    error = null,
                    lastFailedSubmitType = null,
                    successMessage = null,
                )
            }
            is CreatePostUiEvent.ImageSelected -> mutateDraft {
                val current = _uiState.value
                val keepLocation = event.preserveLocation && !event.uri.isNullOrBlank()
                _uiState.value = current.copy(
                    imageUri = event.uri,
                    locationLabel = current.locationLabel.takeIf { keepLocation },
                    latitude = current.latitude.takeIf { keepLocation },
                    longitude = current.longitude.takeIf { keepLocation },
                    locationOrigin = current.locationOrigin.takeIf { keepLocation },
                    error = null,
                    mediaError = null,
                    lastFailedSubmitType = null,
                    successMessage = null,
                )
            }
            is CreatePostUiEvent.VideoSelected -> mutateDraft {
                _uiState.value = _uiState.value.copy(
                    videoUri = event.uri,
                    error = null,
                    mediaError = null,
                    lastFailedSubmitType = null,
                    successMessage = null,
                )
            }
            is CreatePostUiEvent.MediaSelectionFailed -> _uiState.value = _uiState.value.copy(
                mediaError = event.message.takeIf { it.isNotBlank() },
                error = null,
                lastFailedSubmitType = null,
                successMessage = null,
            )
            is CreatePostUiEvent.LocationResolved -> if (applyResolvedLocation(event)) markDraftMutation()
            is CreatePostUiEvent.LocationLabelChanged -> mutateDraft {
                _uiState.value = _uiState.value.copy(
                    locationLabel = event.value.takeIf { it.isNotBlank() },
                    locationOrigin = CreatePostLocationOrigin.Manual,
                    error = null,
                    lastFailedSubmitType = null,
                    successMessage = null,
                )
            }
            CreatePostUiEvent.ReloadDestinations -> loadDestinations()
            CreatePostUiEvent.ClearDraft -> mutateDraft {
                _uiState.value = CreatePostUiState(
                    destinations = _uiState.value.destinations,
                    selectedDestinationWallId = _uiState.value.selectedDestinationWallId,
                    destinationsLoading = _uiState.value.destinationsLoading,
                    destinationsError = _uiState.value.destinationsError,
                )
            }
            CreatePostUiEvent.ClearMediaError -> _uiState.value = _uiState.value.copy(mediaError = null)
            CreatePostUiEvent.Submit -> submit(PostComposerType.Text)
            CreatePostUiEvent.RetrySubmit -> _uiState.value.lastFailedSubmitType?.let(::submit)
            CreatePostUiEvent.ClearMessage -> _uiState.value = _uiState.value.copy(
                successMessage = null,
                error = null,
                mediaError = null,
                lastFailedSubmitType = null,
            )
            CreatePostUiEvent.AuthenticationContinuationHandled -> _uiState.value = _uiState.value.copy(
                authenticationRequiredSubmitType = null,
            )
        }
    }

    private fun applyResolvedLocation(event: CreatePostUiEvent.LocationResolved): Boolean {
        val current = _uiState.value
        if (event.imageUri != null) {
            if (current.imageUri != event.imageUri) return false
            when (event.origin) {
                CreatePostLocationOrigin.Device -> if (current.locationOrigin != null) return false
                CreatePostLocationOrigin.ImageMetadata -> if (current.locationOrigin == CreatePostLocationOrigin.Manual) return false
                CreatePostLocationOrigin.Manual -> Unit
            }
        }
        _uiState.value = current.copy(
            locationLabel = event.label,
            latitude = event.latitude,
            longitude = event.longitude,
            locationOrigin = event.origin,
            error = null,
            lastFailedSubmitType = null,
            successMessage = null,
        )
        return true
    }

    fun draftMutationRevision(): Long = draftMutationRevision

    private fun markDraftMutation() {
        draftMutationRevision += 1
    }

    private inline fun mutateDraft(mutation: () -> Unit) {
        mutation()
        markDraftMutation()
    }

    fun loadDestinations() {
        _uiState.value = _uiState.value.copy(destinationsLoading = true, destinationsError = null)
        scope.launch {
            repository.loadDestinations()
                .onSuccess { destinations ->
                    val cleanDestinations = destinations
                        .filter { it.wallId.isNotBlank() && it.label.isNotBlank() }
                        .distinctBy { it.wallId }
                    val current = _uiState.value
                    val selected = current.selectedDestinationWallId
                        ?.takeIf { id -> cleanDestinations.any { it.wallId == id } }
                        ?: cleanDestinations.firstOrNull { it.isDefault }?.wallId
                        ?: cleanDestinations.firstOrNull()?.wallId
                    _uiState.value = current.copy(
                        destinations = cleanDestinations,
                        selectedDestinationWallId = selected,
                        destinationsLoading = false,
                        destinationsError = null,
                    )
                }
                .onFailure { throwable ->
                    _uiState.value = _uiState.value.copy(
                        destinationsLoading = false,
                        destinationsError = throwable.message,
                    )
                }
        }
    }

    fun snapshot(step: CreatePostStep): PostComposerDraftSnapshot = _uiState.value.let { state ->
        PostComposerDraftSnapshot(
            step = step,
            text = state.text,
            textPatternId = state.textPatternId,
            imageUri = state.imageUri,
            videoUri = state.videoUri,
            locationLabel = state.locationLabel,
            latitude = state.latitude,
            longitude = state.longitude,
            locationOrigin = state.locationOrigin,
            selectedDestinationWallId = state.selectedDestinationWallId,
        )
    }

    fun restore(snapshot: PostComposerDraftSnapshot) {
        cancelSubmit()
        val current = _uiState.value
        _uiState.value = current.copy(
            text = snapshot.text.take(CreatePostTextLimit),
            textPatternId = snapshot.textPatternId,
            imageUri = snapshot.imageUri,
            videoUri = snapshot.videoUri,
            locationLabel = snapshot.locationLabel,
            latitude = snapshot.latitude,
            longitude = snapshot.longitude,
            locationOrigin = snapshot.locationOrigin,
            selectedDestinationWallId = snapshot.selectedDestinationWallId,
            mediaError = null,
            isLoading = false,
            error = null,
            lastFailedSubmitType = null,
            successMessage = null,
            createdPostId = null,
            authenticationRequiredSubmitType = null,
        )
        markDraftMutation()
    }

    fun submit(type: PostComposerType) {
        if (submitJob?.isActive == true) return
        val state = _uiState.value
        val destination = state.selectedDestination
        if (state.destinationsLoading || destination == null) {
            _uiState.value = state.copy(
                isLoading = false,
                error = messages.destinationRequired,
                mediaError = null,
                successMessage = null,
                lastFailedSubmitType = null,
            )
            return
        }
        _uiState.value = state.copy(
            isLoading = true,
            error = null,
            mediaError = null,
            successMessage = null,
            authenticationRequiredSubmitType = null,
        )
        lateinit var runningJob: Job
        runningJob = scope.launch(start = CoroutineStart.LAZY) {
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
                        longitude = state.longitude,
                        destinationWallId = destination?.wallId,
                        destinationLabel = destination?.label,
                    )
                )
                    .also {
                        if (submitJob === runningJob) submitJob = null
                    }
                    .onSuccess { postId ->
                        _uiState.value = CreatePostUiState(
                            destinations = state.destinations,
                            selectedDestinationWallId = state.selectedDestinationWallId,
                            successMessage = messages.created,
                            createdPostId = postId,
                        )
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) {
                            _uiState.value = state.copy(isLoading = false)
                        } else if (throwable is PostComposerAuthenticationRequiredException) {
                            _uiState.value = state.copy(
                                isLoading = false,
                                error = null,
                                lastFailedSubmitType = null,
                                authenticationRequiredSubmitType = type,
                            )
                        } else {
                            _uiState.value = state.copy(
                                isLoading = false,
                                error = throwable.message ?: messages.failed,
                                lastFailedSubmitType = type,
                            )
                        }
                    }
            } finally {
                if (submitJob === runningJob) submitJob = null
            }
        }
        submitJob = runningJob
        runningJob.start()
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

data class CreatePostMessages(
    val created: String,
    val failed: String,
    val destinationRequired: String,
)
