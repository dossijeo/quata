package com.quata.feature.postcomposer.presentation

sealed class CreatePostUiEvent {
    data class TextChanged(val value: String) : CreatePostUiEvent()
    data class ImageSelected(val uri: String?) : CreatePostUiEvent()
    data object Submit : CreatePostUiEvent()
    data object ClearMessage : CreatePostUiEvent()
}
