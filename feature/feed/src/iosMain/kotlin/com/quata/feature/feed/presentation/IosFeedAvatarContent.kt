package com.quata.feature.feed.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.model.Post
import com.quata.core.ui.components.QuataAvatarFrameContent
import com.quata.core.ui.components.QuataLiveRankingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/** iOS image boundary for Feed avatars; all shape, fallback and official UI stays common. */
@Composable
fun IosFeedAuthorAvatar(post: Post, onOpenUserProfile: (String) -> Unit) {
    IosFeedAvatar(
        name = post.author.displayName,
        profileId = post.author.id,
        avatarUrl = post.author.avatarUrl,
        isOfficial = post.author.isOfficial,
        modifier = Modifier
            .size(56.dp)
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .clip(CircleShape)
            .clickable { onOpenUserProfile(post.author.id) },
    )
}

@Composable
fun IosFeedRankingAvatar(item: QuataLiveRankingItem) {
    IosFeedAvatar(
        name = item.avatarName,
        profileId = item.profileId,
        avatarUrl = item.avatarUrl,
        isOfficial = item.isOfficial,
        modifier = Modifier.size(44.dp).clip(CircleShape),
    )
}

@Composable
private fun IosFeedAvatar(
    name: String,
    profileId: String,
    avatarUrl: String?,
    isOfficial: Boolean,
    modifier: Modifier,
) {
    val imageUrl = avatarUrl?.trim()?.takeIf(::isIosAvatarUrl)
    var image by remember(imageUrl) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(imageUrl) {
        image = imageUrl?.let(::loadIosAvatarOrNull)
    }
    QuataAvatarFrameContent(
        name = name,
        stableId = profileId,
        isOfficial = isOfficial,
        modifier = modifier,
        avatar = image?.let { decoded ->
            {
                UIKitView(
                    factory = {
                        UIImageView().apply {
                            contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                            clipsToBounds = true
                            image = decoded
                        }
                    },
                    update = { it.image = decoded },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
}

private suspend fun loadIosAvatarOrNull(url: String): UIImage? = withContext(Dispatchers.Default) {
    runCatching {
        val data = NSData.dataWithContentsOfURL(NSURL(string = url) ?: return@runCatching null)
        data?.let { UIImage(data = it) }
    }.getOrNull()
}

internal fun isIosAvatarUrl(value: String): Boolean =
    value.startsWith("https://") || value.startsWith("http://")
