package com.quata.feature.official.presentation

import com.quata.core.model.User
import com.quata.feature.official.domain.OfficialPostItem

data class OfficialFeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val posts: List<OfficialPostItem> = emptyList(),
    val currentUser: User? = null,
    val isPublishing: Boolean = false,
    val error: String? = null,
    val message: String? = null
)
