package com.quata.feature.feed.presentation

import com.quata.core.model.Post
import com.quata.core.model.User

data class FeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val posts: List<Post> = emptyList(),
    val currentUser: User? = null,
    val error: String? = null
)
