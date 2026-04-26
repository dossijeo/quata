package com.quata.feature.feed.presentation

import com.quata.core.model.Post

data class FeedUiState(
    val isLoading: Boolean = true,
    val posts: List<Post> = emptyList(),
    val error: String? = null
)
