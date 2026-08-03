package com.quata.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.quata.core.model.Post
import com.quata.core.ui.components.QuataAvatarFrameContent
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataAvatarLoadingHaloContent

/**
 * Browser image adapter for the shared Feed identity slots.
 *
 * The common frame owns the circular fallback and official mark.  An image is only supplied
 * after a syntactically safe URL has been accepted; failed browser loads return to the exact
 * same common fallback rather than leaving an empty native element in the reel.
 */
@Composable
fun BrowserFeedAuthorAvatar(
    post: Post,
    onOpenUserProfile: (String) -> Unit,
    isOnline: Boolean? = null,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier.size(56.dp),
) {
    QuataAvatarLoadingHaloContent(isLoading = isLoading, modifier = modifier) {
        BrowserRemoteAvatar(
            name = post.author.displayName,
            profileId = post.author.id,
            avatarUrl = post.author.avatarUrl,
            isOfficial = post.author.isOfficial,
            isOnline = isOnline,
            modifier = Modifier.fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                .clickable(enabled = !isLoading) { onOpenUserProfile(post.author.id) },
        )
    }
}

@Composable
fun BrowserFeedRankingAvatar(item: QuataLiveRankingItem, isOnline: Boolean? = null) {
    BrowserRemoteAvatar(
        name = item.avatarName,
        profileId = item.profileId,
        avatarUrl = item.avatarUrl,
        isOfficial = item.isOfficial,
        isOnline = isOnline,
        modifier = Modifier.size(44.dp),
    )
}

@Composable
fun BrowserRemoteAvatar(
    name: String,
    profileId: String,
    avatarUrl: String?,
    isOfficial: Boolean,
    isOnline: Boolean?,
    modifier: Modifier,
    allowOwnedBlobReference: Boolean = false,
) {
    val imageUrl = avatarUrl?.trim()?.takeIf {
        isBrowserAvatarUrl(it) || (allowOwnedBlobReference && isBrowserAvatarBlobUrl(it))
    }
    val imageState = if (imageUrl != null) rememberBrowserCanvasImage(imageUrl) else null
    QuataAvatarFrameContent(
        name = name,
        stableId = profileId,
        isOfficial = isOfficial,
        isOnline = isOnline,
        modifier = modifier,
        avatar = (imageState as? BrowserCanvasImageState.Ready)?.let { ready ->
            {
                Image(
                    painter = BitmapPainter(ready.bitmap),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
}

internal fun isBrowserAvatarUrl(value: String): Boolean =
    value.startsWith("https://") || value.startsWith("http://")

/** Only Profile opts into its locally-owned preview URLs; feed data never does. */
internal fun isBrowserAvatarBlobUrl(value: String): Boolean =
    value.startsWith("blob:", ignoreCase = true) && value.length > "blob:".length
