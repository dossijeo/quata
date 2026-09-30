package com.quata.feature.postcomposer.presentation

sealed class CreatePostUiEvent {
    data class TextChanged(val value: String) : CreatePostUiEvent()
    data class TextPatternSelected(val patternId: String) : CreatePostUiEvent()
    data class DestinationSelected(val wallId: String) : CreatePostUiEvent()
    data class ImageSelected(
        val uri: String?,
        val preserveLocation: Boolean = false,
    ) : CreatePostUiEvent()
    data class VideoSelected(val uri: String?) : CreatePostUiEvent()
    data class MediaSelectionFailed(val message: String) : CreatePostUiEvent()
    data class LocationResolved(
        val label: String,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val origin: CreatePostLocationOrigin = CreatePostLocationOrigin.Device,
        val imageUri: String? = null,
    ) : CreatePostUiEvent()
    data class LocationLabelChanged(val value: String) : CreatePostUiEvent()
    data object ReloadDestinations : CreatePostUiEvent()
    data object ClearDraft : CreatePostUiEvent()
    data object ClearMediaError : CreatePostUiEvent()
    data object Submit : CreatePostUiEvent()
    data object RetrySubmit : CreatePostUiEvent()
    data object ClearMessage : CreatePostUiEvent()
    data object AuthenticationContinuationHandled : CreatePostUiEvent()
}
