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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.model.Post
import com.quata.core.ui.components.QuataAvatarFrameContent
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.data.toFoundationData
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/** iOS image boundary for Feed avatars; all shape, fallback and official UI stays common. */
@Composable
fun IosFeedAuthorAvatar(post: Post, onOpenUserProfile: (String) -> Unit, isOnline: Boolean? = null) {
    IosRemoteAvatar(
        name = post.author.displayName,
        profileId = post.author.id,
        avatarUrl = post.author.avatarUrl,
        isOfficial = post.author.isOfficial,
        isOnline = isOnline,
        modifier = Modifier
            .size(56.dp)
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .clickable { onOpenUserProfile(post.author.id) },
    )
}

@Composable
fun IosFeedRankingAvatar(item: QuataLiveRankingItem, isOnline: Boolean? = null) {
    IosRemoteAvatar(
        name = item.avatarName,
        profileId = item.profileId,
        avatarUrl = item.avatarUrl,
        isOfficial = item.isOfficial,
        isOnline = isOnline,
        modifier = Modifier.size(44.dp),
    )
}

/** Shared iOS remote-avatar adapter for Compose feature slots. */
@Composable
fun IosRemoteAvatar(
    name: String,
    profileId: String,
    avatarUrl: String?,
    isOfficial: Boolean,
    isOnline: Boolean?,
    modifier: Modifier,
) {
    val imageUrl = avatarUrl?.trim()?.takeIf(::isIosAvatarUrl)
    var image by remember(imageUrl) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(imageUrl) {
        image = if (imageUrl == null) null else loadIosAvatarOrNull(imageUrl)
    }
    QuataAvatarFrameContent(
        name = name,
        stableId = profileId,
        isOfficial = isOfficial,
        isOnline = isOnline,
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

private suspend fun loadIosAvatarOrNull(url: String): UIImage? =
    runCatching { iosAvatarData(NSURL(string = url) ?: return@runCatching null) }
        .getOrNull()
        ?.let { UIImage(data = it) }

@OptIn(ExperimentalForeignApi::class)
private suspend fun iosAvatarData(url: NSURL): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosAvatarDataDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(
        NSURLSessionConfiguration.ephemeralSessionConfiguration(), delegate, null,
    )
    val task = session.dataTaskWithURL(url)
    continuation.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class IosAvatarDataDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toAvatarBytes()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        val data = chunks.toFoundationData().takeIf { it.length > 0uL }
        if (status !in 200..299 || data == null) {
            continuation.resumeWithException(IllegalStateException("ios_avatar_unavailable"))
        } else {
            continuation.resume(data)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toAvatarBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

internal fun isIosAvatarUrl(value: String): Boolean =
    value.startsWith("https://") || value.startsWith("http://")
