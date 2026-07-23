package com.quata.core.ui.components

data class QuataLiveRankingItem(
    val id: String,
    val profileId: String,
    val rank: Int,
    val title: String,
    val subtitle: String,
    val avatarName: String,
    val avatarUrl: String?,
    val isOfficial: Boolean,
    val likesCount: Int
)
