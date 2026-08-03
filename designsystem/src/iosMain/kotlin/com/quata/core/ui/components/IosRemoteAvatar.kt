package com.quata.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
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

/** Shared iOS boundary for remote avatars used by Feed, Profile and future feature hosts. */
@Composable
fun IosRemoteAvatar(
    name: String,
    stableId: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    isOfficial: Boolean = false,
    isOnline: Boolean? = null,
) {
    val imageUrl = avatarUrl?.trim()?.takeIf(::isIosRemoteAvatarUrl)
    var image by remember(imageUrl) { mutableStateOf<ImageBitmap?>(null) }
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
                Image(
                    bitmap = decoded,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
}

internal fun isIosRemoteAvatarUrl(value: String): Boolean =
    value.startsWith("https://") || value.startsWith("http://")

private suspend fun loadIosRemoteAvatarOrNull(url: String): ImageBitmap? =
    runCatching { iosRemoteAvatarData(NSURL(string = url) ?: return@runCatching null) }
        .getOrNull()
        ?.toIosRemoteAvatarBytes()
        ?.takeIf(ByteArray::isNotEmpty)
        ?.let { encoded -> runCatching { encoded.decodeToImageBitmap() }.getOrNull() }

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
