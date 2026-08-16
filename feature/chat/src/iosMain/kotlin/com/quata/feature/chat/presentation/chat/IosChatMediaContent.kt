package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.zIndex
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.ui.components.QuataFullscreenMediaOverlayMediaCloseTestTag
import com.quata.feature.chat.data.IosChatAttachmentDownloader
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIView

/** Swift/AVFoundation boundary behind the common full-screen Chat media overlay. */
interface IosChatMediaViewerFactory {
    fun create(localUrl: String, isVideo: Boolean): IosChatMediaViewerSurface
    fun createCloseButton(
        action: IosChatMediaOverlayCloseAction,
        accessibilityIdentifier: String,
    ): UIView
}

interface IosChatMediaViewerSurface {
    fun nativeView(): UIView
    fun dispose()
}

interface IosChatMediaOverlayCloseAction {
    fun close()
}

internal fun iosChatMediaPlatformSlots(
    downloader: IosChatAttachmentDownloader,
    viewerFactory: IosChatMediaViewerFactory,
    retryLabel: String,
) = ChatMediaPlatformSlots(
    preview = { file, kind, modifier ->
        IosChatMediaPreview(file, kind, downloader, retryLabel, modifier)
    },
    viewer = { file, kind, modifier ->
        IosChatMediaViewer(file, kind, downloader, viewerFactory, retryLabel, modifier)
    },
    nativeClose = { onDismiss ->
        IosChatNativeMediaCloseButton(
            onDismiss = onDismiss,
            viewerFactory = viewerFactory,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .zIndex(8f)
                .padding(16.dp)
                .size(44.dp),
        )
    },
)

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun IosChatNativeMediaCloseButton(
    onDismiss: () -> Unit,
    viewerFactory: IosChatMediaViewerFactory,
    modifier: Modifier,
) {
    val action = remember { IosChatMediaOverlayCloseActionAdapter() }
    action.onDismiss = onDismiss
    UIKitView(
        factory = {
            viewerFactory.createCloseButton(
                action = action,
                accessibilityIdentifier = QuataFullscreenMediaOverlayMediaCloseTestTag,
            )
        },
        modifier = modifier,
    )
}

private class IosChatMediaOverlayCloseActionAdapter : IosChatMediaOverlayCloseAction {
    var onDismiss: () -> Unit = {}

    override fun close() {
        onDismiss()
    }
}

@Composable
private fun IosChatMediaPreview(
    file: PlatformFile,
    kind: ChatAttachmentKind,
    downloader: IosChatAttachmentDownloader,
    retryLabel: String,
    modifier: Modifier,
) {
    val download = rememberIosChatMediaDownload(file, downloader)
    var image by remember(download.file?.reference, kind) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(download.file?.reference, kind) {
        image = download.file?.let { decodeIosChatMediaPreview(it, kind) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val decoded = image
        if (decoded != null) {
            Image(
                bitmap = decoded,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (download.isLoading) {
            CircularProgressIndicator()
        } else {
            ChatMediaLoadFailureContent(
                retryLabel = retryLabel,
                onRetry = download.retry,
            )
        }
    }
}

@Composable
private fun IosChatMediaViewer(
    file: PlatformFile,
    kind: ChatAttachmentKind,
    downloader: IosChatAttachmentDownloader,
    viewerFactory: IosChatMediaViewerFactory,
    retryLabel: String,
    modifier: Modifier,
) {
    val download = rememberIosChatMediaDownload(file, downloader)
    var image by remember(download.file?.reference, kind) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(download.file?.reference, kind) {
        image = if (kind == ChatAttachmentKind.Image) {
            download.file?.let { decodeIosChatMediaPreview(it, kind) }
        } else {
            null
        }
    }
    val surface = remember(download.file?.reference, kind, viewerFactory) {
        download.file
            ?.takeIf { kind == ChatAttachmentKind.Video }
            ?.let { viewerFactory.create(it.reference, isVideo = true) }
    }
    DisposableEffect(surface) { onDispose { surface?.dispose() } }
    Box(modifier, contentAlignment = Alignment.Center) {
        val decoded = image
        if (download.isLoading) {
            CircularProgressIndicator()
        } else if (kind == ChatAttachmentKind.Image && decoded != null) {
            Image(
                bitmap = decoded,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (surface == null) {
            ChatMediaLoadFailureContent(
                retryLabel = retryLabel,
                onRetry = download.retry,
            )
        } else {
            UIKitView(factory = surface::nativeView, modifier = Modifier.fillMaxSize())
        }
    }
}

internal data class IosChatMediaDownloadState(
    val attempt: Int = 0,
    val file: PlatformFile? = null,
    val isLoading: Boolean = true,
) {
    val hasFailed: Boolean get() = !isLoading && file == null

    fun retry(): IosChatMediaDownloadState = copy(
        attempt = attempt + 1,
        file = null,
        isLoading = true,
    )

    fun complete(result: PlatformResult<PlatformFile>): IosChatMediaDownloadState = when (result) {
        is PlatformResult.Success -> copy(file = result.value, isLoading = false)
        is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported ->
            copy(file = null, isLoading = false)
    }
}

private data class IosChatMediaDownloadHandle(
    val state: IosChatMediaDownloadState,
    val retry: () -> Unit,
) {
    val file: PlatformFile? get() = state.file
    val isLoading: Boolean get() = state.isLoading
}

@Composable
private fun rememberIosChatMediaDownload(
    remote: PlatformFile,
    downloader: IosChatAttachmentDownloader,
): IosChatMediaDownloadHandle {
    var state by remember(remote.reference) { mutableStateOf(IosChatMediaDownloadState()) }
    LaunchedEffect(remote.reference, remote.displayName, remote.mimeType, downloader, state.attempt) {
        state = state.complete(downloader.download(remote.reference, remote.displayName))
    }
    val localFile = state.file
    DisposableEffect(localFile, downloader) {
        onDispose { localFile?.let(downloader::discard) }
    }
    return IosChatMediaDownloadHandle(
        state = state,
        retry = { state = state.retry() },
    )
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun decodeIosChatMediaPreview(file: PlatformFile, kind: ChatAttachmentKind): ImageBitmap? =
    withContext(Dispatchers.Default) {
        val url = NSURL(string = file.reference) ?: return@withContext null
        val encoded = runCatching<ByteArray?> {
            if (kind == ChatAttachmentKind.Video) {
                AVAssetImageGenerator(AVURLAsset(uRL = url, options = null)).apply {
                    appliesPreferredTrackTransform = true
                }.copyCGImageAtTime(CMTimeMakeWithSeconds(0.0, 600), null, null)
                    ?.let(UIImage::imageWithCGImage)
                    ?.let { image -> UIImagePNGRepresentation(image) }
                    ?.toIosChatPreviewBytes()
            } else {
                val path = url.path ?: return@runCatching null
                NSFileManager.defaultManager.contentsAtPath(path)?.toIosChatPreviewBytes()
            }
        }.getOrNull()?.takeIf(ByteArray::isNotEmpty)
        encoded?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosChatPreviewBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)
