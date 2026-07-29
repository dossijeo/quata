package com.quata.web

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.core.model.Post
import com.quata.core.ui.components.QuataAvatarFrameContent
import com.quata.core.ui.components.QuataLiveRankingItem
import org.w3c.dom.HTMLImageElement
import kotlinx.browser.document

/**
 * Browser image adapter for the shared Feed identity slots.
 *
 * The common frame owns the circular fallback and official mark.  An image is only supplied
 * after a syntactically safe URL has been accepted; failed browser loads return to the exact
 * same common fallback rather than leaving an empty native element in the reel.
 */
@Composable
fun BrowserFeedAuthorAvatar(post: Post, onOpenUserProfile: (String) -> Unit) {
    BrowserFeedAvatar(
        name = post.author.displayName,
        profileId = post.author.id,
        avatarUrl = post.author.avatarUrl,
        isOfficial = post.author.isOfficial,
        modifier = Modifier
            .size(56.dp)
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .clickable { onOpenUserProfile(post.author.id) },
    )
}

@Composable
fun BrowserFeedRankingAvatar(item: QuataLiveRankingItem) {
    BrowserFeedAvatar(
        name = item.avatarName,
        profileId = item.profileId,
        avatarUrl = item.avatarUrl,
        isOfficial = item.isOfficial,
        modifier = Modifier.size(44.dp),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BrowserFeedAvatar(
    name: String,
    profileId: String,
    avatarUrl: String?,
    isOfficial: Boolean,
    modifier: Modifier,
) {
    val imageUrl = avatarUrl?.trim()?.takeIf(::isBrowserAvatarUrl)
    var failedUrl by remember(imageUrl) { mutableStateOf<String?>(null) }
    val usableImageUrl = imageUrl?.takeUnless { it == failedUrl }
    QuataAvatarFrameContent(
        name = name,
        stableId = profileId,
        isOfficial = isOfficial,
        modifier = modifier,
        avatar = usableImageUrl?.let { url ->
            {
                WebElementView(
                    factory = {
                        (document.createElement("img") as HTMLImageElement).apply {
                            alt = name
                            setAttribute("loading", "lazy")
                            style.width = "100%"
                            style.height = "100%"
                            style.objectFit = "cover"
                            addEventListener("error", { failedUrl = url })
                        }
                    },
                    update = { image ->
                        image.alt = name
                        image.src = url
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
}

internal fun isBrowserAvatarUrl(value: String): Boolean =
    value.startsWith("https://") || value.startsWith("http://")
