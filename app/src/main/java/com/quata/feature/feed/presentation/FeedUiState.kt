package com.quata.feature.feed.presentation

import com.quata.core.model.Post
import com.quata.core.model.User

data class FeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlderPosts: Boolean = true,
    val posts: List<Post> = emptyList(),
    val currentUser: User? = null,
    val error: String? = null
)
