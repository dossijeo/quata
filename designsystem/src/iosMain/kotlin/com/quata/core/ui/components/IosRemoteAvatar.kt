package com.quata.core.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
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
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject

/** Shared iOS boundary for remote avatars used by Feed, Profile and future feature hosts. */
@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun IosRemoteAvatar(
    name: String,
    stableId: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    isOfficial: Boolean = false,
    isOnline: Boolean? = null,
) {
    val imageUrl = avatarUrl?.trim()?.takeIf(::isIosRemoteAvatarUrl)
    var image by remember(imageUrl) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(imageUrl) {
        image = if (imageUrl == null) null else loadIosRemoteAvatarOrNull(imageUrl)
    }
    QuataAvatarFrameContent(
        name = name,
        stableId = stableId,
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
                    // Identity actions belong to the common Compose frame. A decorative
                    // UIImageView must never consume the tap that opens the public profile.
                    properties = UIKitInteropProperties(interactionMode = null),
                )
            }
        },
    )
}

internal fun isIosRemoteAvatarUrl(value: String): Boolean =
    value.startsWith("https://") || value.startsWith("http://")

private suspend fun loadIosRemoteAvatarOrNull(url: String): UIImage? =
    runCatching { iosRemoteAvatarData(NSURL(string = url) ?: return@runCatching null) }
        .getOrNull()
        ?.let { UIImage(data = it) }

@OptIn(ExperimentalForeignApi::class)
private suspend fun iosRemoteAvatarData(url: NSURL): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosRemoteAvatarDataDelegate(continuation)
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
private class IosRemoteAvatarDataDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toIosRemoteAvatarBytes()
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
private fun NSData.toIosRemoteAvatarBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)
