package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.feed.presentation.FeedScreenHost
import com.quata.feature.feed.presentation.FeedScreenPlatformSlots

/** Browser route adapter. Product rendering is the common [FeedScreenHost], including details. */
@Composable
fun WebFeedHost(
    repository: WebFeedRepository,
    sharedPostId: String? = null,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(sharedPostId) { setWebFeedDetailMarker(sharedPostId) }
    // One host-level value intentionally applies to every reel, as on Android.
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
        ),
        modifier = modifier,
    )
}

private fun setWebFeedDetailMarker(postId: String?) {
    js("globalThis.document?.documentElement?.setAttribute('data-quata-feed-detail', postId || '')")
}
