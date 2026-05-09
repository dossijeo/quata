package com.quata.feature.postcomposer.presentation

sealed class CreatePostUiEvent {
    data class TextChanged(val value: String) : CreatePostUiEvent()
    data class ImageSelected(val uri: String?) : CreatePostUiEvent()
    data class VideoSelected(val uri: String?) : CreatePostUiEvent()
    data class LocationResolved(
        val label: String,
        val latitude: Double? = null,
        val longitude: Double? = null
    ) : CreatePostUiEvent()
    data object Submit : CreatePostUiEvent()
    data object ClearMessage : CreatePostUiEvent()
}
