package com.quata.feature.postcomposer.presentation

import com.quata.core.ui.DEFAULT_TEXT_CANVAS_PATTERN_ID

data class CreatePostUiState(
    val text: String = "",
    val textPatternId: String = DEFAULT_TEXT_CANVAS_PATTERN_ID,
    val imageUri: String? = null,
    val videoUri: String? = null,
    val locationLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val createdPostId: String? = null
)
