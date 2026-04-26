package com.quata.feature.postcomposer.presentation

data class CreatePostUiState(
    val text: String = "",
    val imageUri: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
