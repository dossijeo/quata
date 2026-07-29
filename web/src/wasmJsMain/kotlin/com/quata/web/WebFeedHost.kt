package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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
    FeedScreenHost(
        padding = PaddingValues(),
        repository = repository,
        focusedPostId = sharedPostId,
        slots = FeedScreenPlatformSlots(media = { post, _, _, _ -> BrowserFeedMediaContent(post) }),
        modifier = modifier,
    )
}

private fun setWebFeedDetailMarker(postId: String?) {
    js("globalThis.document?.documentElement?.setAttribute('data-quata-feed-detail', postId || '')")
}
