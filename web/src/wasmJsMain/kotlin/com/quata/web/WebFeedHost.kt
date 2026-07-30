package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onOpenUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(sharedPostId) { setWebFeedDetailMarker(sharedPostId) }
    var isFeedMuted by rememberSaveable { mutableStateOf(false) }
    val windowLayout = rememberQuataWindowLayoutInfo()
    FeedScreenHost(
        padding = PaddingValues(),
        repository = repository,
        focusedPostId = sharedPostId,
        isLandscape = windowLayout.isLandscape,
        slots = FeedScreenPlatformSlots(
            media = { post, isCurrent, initialPositionMs, onPositionChanged ->
                BrowserFeedMediaContent(
                    post = post,
                    isCurrent = isCurrent,
                    isMuted = isFeedMuted,
                    initialPositionMs = initialPositionMs,
                    onPositionChanged = onPositionChanged,
                    onMuteChange = { isFeedMuted = it },
                )
            },
            avatar = { post -> BrowserFeedAuthorAvatar(post, onOpenUserProfile) },
            rankingAvatar = { item -> BrowserFeedRankingAvatar(item) },
            avatarWithPresence = { post, isOnline -> BrowserFeedAuthorAvatar(post, onOpenUserProfile, isOnline) },
            rankingAvatarWithPresence = { item, isOnline -> BrowserFeedRankingAvatar(item, isOnline) },
            share = shareService::share,
            showComposeMessage = true,
        ),
        presence = presence,
        modifier = modifier,
    )
}

private fun setWebFeedDetailMarker(postId: String?) {
    js("globalThis.document?.documentElement?.setAttribute('data-quata-feed-detail', postId || '')")
}
