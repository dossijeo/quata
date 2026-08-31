package com.quata.feature.feed.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.core.ui.components.IosRemoteAvatar
import com.quata.core.ui.components.QuataAvatarLoadingHaloContent
import com.quata.core.ui.components.QuataLiveRankingItem

@Composable
fun IosFeedAuthorAvatar(
    post: Post,
    onOpenUserProfile: (String) -> Unit,
    isOnline: Boolean? = null,
    isLoading: Boolean = false,
) {
    @Suppress("UNUSED_VARIABLE")
    val commonRowOwnsProfileNavigation = onOpenUserProfile
    QuataAvatarLoadingHaloContent(isLoading = isLoading, modifier = Modifier.size(56.dp)) {
        IosRemoteAvatar(
            name = post.author.displayName,
            stableId = post.author.id,
            avatarUrl = post.author.avatarUrl,
            isOfficial = post.author.isOfficial,
            isOnline = isOnline,
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        )
    }
}

@Composable
fun IosFeedRankingAvatar(item: QuataLiveRankingItem, isOnline: Boolean? = null) {
    IosRemoteAvatar(
        name = item.avatarName,
        stableId = item.profileId,
        avatarUrl = item.avatarUrl,
        isOfficial = item.isOfficial,
        isOnline = isOnline,
        modifier = Modifier.size(44.dp),
    )
}

internal fun isIosAvatarUrl(value: String): Boolean =
    value.startsWith("https://") || value.startsWith("http://")
