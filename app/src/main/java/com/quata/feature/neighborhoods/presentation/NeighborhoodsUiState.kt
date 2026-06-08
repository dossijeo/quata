package com.quata.feature.neighborhoods.presentation

import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.CommunityUserProfile

data class NeighborhoodsUiState(
    val isLoading: Boolean = true,
    val communities: List<NeighborhoodCommunity> = emptyList(),
    val isOpeningChat: Boolean = false,
    val openingChatNeighborhood: String? = null,
    val openingPrivateChatUserId: String? = null,
    val openingProfileUserId: String? = null,
    val refreshingProfileUserId: String? = null,
    val followingUserId: String? = null,
    val selectedProfile: CommunityUserProfile? = null,
    val error: String? = null
)
