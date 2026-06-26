package com.quata.feature.feed.presentation

import com.quata.core.model.Post

data class FeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val posts: List<Post> = emptyList(),
    val error: String? = null
)
