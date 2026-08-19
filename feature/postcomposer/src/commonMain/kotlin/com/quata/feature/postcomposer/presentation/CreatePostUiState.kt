package com.quata.feature.postcomposer.presentation

import com.quata.feature.postcomposer.domain.PostComposerDestination

data class CreatePostUiState(
    val text: String = "",
    val textPatternId: String = DEFAULT_TEXT_CANVAS_PATTERN_ID,
    val imageUri: String? = null,
    val videoUri: String? = null,
    val locationLabel: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val destinations: List<PostComposerDestination> = emptyList(),
    val selectedDestinationWallId: String? = null,
    val destinationsLoading: Boolean = false,
    val destinationsError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val createdPostId: String? = null
) {
    val selectedDestination: PostComposerDestination?
        get() = destinations.firstOrNull { it.wallId == selectedDestinationWallId }
            ?: destinations.firstOrNull { it.isDefault }
            ?: destinations.firstOrNull()
}

const val DEFAULT_TEXT_CANVAS_PATTERN_ID = "midnight-blue"
