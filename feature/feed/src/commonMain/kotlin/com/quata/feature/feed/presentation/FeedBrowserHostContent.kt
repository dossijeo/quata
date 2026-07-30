package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post

/** Strings retained exclusively by the reusable post-detail surface. */
data class FeedBrowserHostStrings(
    val loading: String,
    val retry: String,
    val loadFailure: String,
    val refresh: String,
    val refreshing: String,
    val conversations: String,
    val loadingOlder: String,
    val loadOlder: String,
    val noText: String,
    val readMore: String,
    val close: String,
    val empty: String,
    val mediaUnavailable: String,
    val backToFeed: String = "Back to feed",
    val detailLoading: String = "Loading post\u2026",
    val detailUnavailable: String = "This post is no longer available.",
)

/**
 * Reusable card used only by the post-detail route. The obsolete browser Feed root and its
 * status/action controls deliberately do not exist: platform feeds render [FeedScreenHost].
 */
@Composable
fun FeedBrowserPostContent(
    post: Post,
    strings: FeedBrowserHostStrings,
    mediaContent: @Composable (Post) -> Unit = { item -> FeedBrowserMediaUnavailableContent(item, strings) },
    author: @Composable (Post, Modifier) -> Unit = { item, modifier ->
        FeedPostMetadataContent(item.author.displayName, item.createdAt, modifier)
    },
    navigation: @Composable BoxScope.(Post) -> Unit = {},
    actionRail: @Composable BoxScope.(Post) -> Unit = {},
) {
    val hasPlatformMedia = post.imageUrl != null || post.videoUrl != null
    FeedPostPreviewCardContent(
        author = { modifier -> author(post, modifier) },
        media = {
            if (hasPlatformMedia) mediaContent(post) else TextOnlyReelContent(
                stableId = post.id,
                displayText = post.text.ifBlank { strings.noText },
                seedText = post.id,
                patternId = null,
                readMoreText = strings.readMore,
                readerDismissButton = { buttonModifier, onDismiss ->
                    Button(onClick = onDismiss, modifier = buttonModifier) { Text(strings.close) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        actionRail = { actionRail(post) },
        navigation = { navigation(post) },
        body = { if (hasPlatformMedia && post.text.isNotBlank()) Text(post.text, style = MaterialTheme.typography.bodyMedium) },
    )
}

@Composable
internal fun FeedBrowserMediaUnavailableContent(post: Post, strings: FeedBrowserHostStrings) {
    if (post.imageUrl != null || post.videoUrl != null) {
        FeedMediaUnavailablePlaceholderContent(strings.mediaUnavailable, Modifier.padding(16.dp))
    }
}

@Composable
fun FeedMediaUnavailablePlaceholderContent(message: String, modifier: Modifier = Modifier) {
    Text(message, style = feedMediaUnavailableTextStyle(MaterialTheme.typography.bodySmall), modifier = modifier)
}

internal fun feedMediaUnavailableTextStyle(base: TextStyle): TextStyle =
    base.copy(color = FeedMediaUnavailableContentColor)
