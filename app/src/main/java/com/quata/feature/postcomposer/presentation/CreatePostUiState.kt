package com.quata.feature.postcomposer.presentation

data class CreatePostUiState(
    val text: String = "",
    val imageUri: String? = null,
    val videoUri: String? = null,
    val locationLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
