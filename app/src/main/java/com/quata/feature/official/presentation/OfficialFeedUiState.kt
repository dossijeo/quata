package com.quata.feature.official.presentation

import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostItem

data class OfficialFeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreOlderPosts: Boolean = true,
    val posts: List<OfficialPostItem> = emptyList(),
    val currentUser: User? = null,
    val isPublishing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val createdPostId: String? = null
)

internal object OfficialFeedMessages {
    const val PostCreated = "post_created"
    const val PostDeleted = "post_deleted"
}
