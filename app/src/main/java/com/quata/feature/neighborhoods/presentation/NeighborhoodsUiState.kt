package com.quata.feature.neighborhoods.presentation

import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity

data class NeighborhoodsUiState(
    val isLoading: Boolean = true,
    val communities: List<NeighborhoodCommunity> = emptyList(),
    val isOpeningChat: Boolean = false,
    val error: String? = null
)
