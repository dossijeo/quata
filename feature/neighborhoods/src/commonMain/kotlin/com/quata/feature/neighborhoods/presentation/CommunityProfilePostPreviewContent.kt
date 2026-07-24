package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.model.Post
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.text.withoutPostShortcodes
import com.quata.core.ui.textCanvasBrush

/**
 * Shared profile-gallery post composition.
 *
 * Hosts render the actual image/video surface and retain sharing, reporting and authorization
 * policy. The portable layer owns the text fallback, metadata, optimistic like state and action
 * rail, so every platform presents the same interaction structure without importing media APIs.
 */
@Composable
fun CommunityProfilePostPreviewContent(
    post: Post,
    commentsCount: Int,
    canParticipate: Boolean,
    onOpenComments: () -> Unit,
    onAuthRequired: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    media: @Composable BoxScope.(isVideoLoaded: Boolean, onLoadVideo: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var liked by rememberSaveable(post.id, post.isLikedByCurrentUser) {
        mutableStateOf(post.isLikedByCurrentUser)
    }
    var isVideoLoaded by rememberSaveable(post.id) { mutableStateOf(false) }
    val likeDelta = when {
        liked && !post.isLikedByCurrentUser -> 1
        !liked && post.isLikedByCurrentUser -> -1
        else -> 0
    }
    val likes = (post.likesCount + likeDelta).coerceAtLeast(0)
    val cleanPostText = remember(post.text) { post.text.withoutPostShortcodes() }
    val seedText = remember(post.text) { post.text.cleanTextCanvasSeedBody() }
    val mediaSeed = post.imageUrl ?: post.videoUrl ?: seedText

    ProfilePostPreviewFrameContent(
        backgroundSeed = mediaSeed,
        modifier = modifier,
        media = {
            if (post.imageUrl != null || post.videoUrl != null) {
                media(isVideoLoaded) { isVideoLoaded = true }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .background(textCanvasBrush(seedText))
                        .padding(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cleanPostText,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                    )
                }
            }
        },
        metadata = {
            Text(post.author.displayName, fontWeight = FontWeight.ExtraBold, color = Color.White)
            val subtitle = when {
                post.imageUrl != null && post.videoUrl == null -> post.placeName.orEmpty()
                post.videoUrl != null -> cleanPostText
                else -> ""
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actionRail = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                ProfilePostActionContent(
                    icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    count = likes.toString(),
                    onClick = {
                        if (canParticipate) liked = !liked else onAuthRequired()
                    },
                )
                ProfilePostActionContent(Icons.Filled.ChatBubble, commentsCount.toString(), onClick = onOpenComments)
                ProfilePostActionContent(Icons.Filled.Share, null, onClick = onShare)
                ProfilePostActionContent(
                    icon = Icons.Filled.Flag,
                    count = null,
                    tint = if (post.isReportedByCurrentUser) QuataOrange else Color.White,
                    onClick = onReport,
                )
            }
        },
    )
}
