package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.quata.core.platform.ShareService
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.feed.presentation.FeedScreenHost
import com.quata.feature.feed.presentation.FeedScreenPlatformSlots
import com.quata.feature.feed.presentation.FeedUserPresence

/** Browser route adapter. Product rendering is the common [FeedScreenHost], including details. */
@Composable
fun WebFeedHost(
    repository: WebFeedRepository,
    shareService: ShareService,
    presence: FeedUserPresence? = null,
    sharedPostId: String? = null,
    currentUserId: String? = null,
    openingProfileUserId: String? = null,
    onAuthRequired: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(sharedPostId) { setWebFeedDetailMarker(sharedPostId) }
    val windowLayout = rememberQuataWindowLayoutInfo()
    FeedScreenHost(
        padding = PaddingValues(),
        repository = repository,
        focusedPostId = sharedPostId,
        isLandscape = windowLayout.isLandscape,
        slots = FeedScreenPlatformSlots(
            media = { post, isCurrent, initialPositionMs, onPositionChanged, isFeedMuted, onFeedMuteChange ->
                BrowserFeedMediaContent(
                    post = post,
                    isCurrent = isCurrent,
                    isMuted = isFeedMuted,
                    initialPositionMs = initialPositionMs,
                    onPositionChanged = onPositionChanged,
                    onMuteChange = onFeedMuteChange,
                )
            },
            avatar = { post -> BrowserFeedAuthorAvatar(post, onOpenUserProfile, isLoading = openingProfileUserId == post.author.id) },
            rankingAvatar = { item -> BrowserFeedRankingAvatar(item) },
            avatarWithPresence = { post, isOnline -> BrowserFeedAuthorAvatar(post, onOpenUserProfile, isOnline, openingProfileUserId == post.author.id) },
            rankingAvatarWithPresence = { item, isOnline -> BrowserFeedRankingAvatar(item, isOnline) },
            share = shareService::share,
            showComposeMessage = true,
        ),
        presence = presence,
        currentUserId = currentUserId,
        onAuthRequired = onAuthRequired,
        onCreatePost = onCreatePost,
        modifier = modifier,
    )
}

private fun setWebFeedDetailMarker(postId: String?) {
    js("globalThis.document?.documentElement?.setAttribute('data-quata-feed-detail', postId || '')")
}
