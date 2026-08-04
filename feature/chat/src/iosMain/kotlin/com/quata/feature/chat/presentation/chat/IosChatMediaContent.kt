package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.feature.chat.data.IosChatAttachmentDownloader
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode

/** Swift/AVFoundation boundary behind the common full-screen Chat media overlay. */
interface IosChatMediaViewerFactory {
    fun create(localUrl: String, isVideo: Boolean): IosChatMediaViewerSurface
}

interface IosChatMediaViewerSurface {
    fun nativeView(): UIView
    fun dispose()
}

internal fun iosChatMediaPlatformSlots(
    downloader: IosChatAttachmentDownloader,
    viewerFactory: IosChatMediaViewerFactory,
) = ChatMediaPlatformSlots(
    preview = { file, kind, modifier ->
        IosChatMediaPreview(file, kind, downloader, modifier)
    },
    viewer = { file, kind, modifier ->
        IosChatMediaViewer(file, kind, downloader, viewerFactory, modifier)
    },
)

@Composable
private fun IosChatMediaPreview(
    file: PlatformFile,
    kind: ChatAttachmentKind,
    downloader: IosChatAttachmentDownloader,
    modifier: Modifier,
) {
    val download = rememberIosChatMediaDownload(file, downloader)
    var image by remember(download.file?.reference, kind) { mutableStateOf<UIImage?>(null) }
    LaunchedEffect(download.file?.reference, kind) {
        image = download.file?.let { decodeIosChatMediaPreview(it, kind) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val decoded = image
        if (decoded != null) {
            UIKitView(
                factory = {
                    UIImageView().apply {
                        contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                        clipsToBounds = true
                    }
                },
                update = { it.image = decoded },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (download.isLoading) {
            CircularProgressIndicator()
        } else {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = file.displayName)
        }
    }
}

@Composable
private fun IosChatMediaViewer(
    file: PlatformFile,
    kind: ChatAttachmentKind,
    downloader: IosChatAttachmentDownloader,
    viewerFactory: IosChatMediaViewerFactory,
    modifier: Modifier,
) {
    val download = rememberIosChatMediaDownload(file, downloader)
    val surface = remember(download.file?.reference, kind, viewerFactory) {
        download.file?.let { viewerFactory.create(it.reference, kind == ChatAttachmentKind.Video) }
    }
    DisposableEffect(surface) { onDispose { surface?.dispose() } }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (download.isLoading) {
            CircularProgressIndicator()
        } else if (surface == null) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = file.displayName)
        } else {
            UIKitView(factory = surface::nativeView, modifier = Modifier.fillMaxSize())
        }
    }
}

private data class IosChatMediaDownloadState(
    val file: PlatformFile? = null,
    val isLoading: Boolean = true,
)

@Composable
private fun rememberIosChatMediaDownload(
    remote: PlatformFile,
    downloader: IosChatAttachmentDownloader,
): IosChatMediaDownloadState {
    var state by remember(remote.reference) { mutableStateOf(IosChatMediaDownloadState()) }
    LaunchedEffect(remote.reference, remote.displayName, remote.mimeType, downloader) {
        state = IosChatMediaDownloadState()
        state = when (val result = downloader.download(remote.reference, remote.displayName)) {
            is PlatformResult.Success -> IosChatMediaDownloadState(file = result.value, isLoading = false)
            is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported ->
                IosChatMediaDownloadState(isLoading = false)
        }
    }
    val localFile = state.file
    DisposableEffect(localFile, downloader) {
        onDispose { localFile?.let(downloader::discard) }
    }
    return state
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun decodeIosChatMediaPreview(file: PlatformFile, kind: ChatAttachmentKind): UIImage? =
    withContext(Dispatchers.Default) {
        val url = NSURL(string = file.reference) ?: return@withContext null
        runCatching {
            if (kind == ChatAttachmentKind.Video) {
                AVAssetImageGenerator(AVURLAsset(uRL = url, options = null)).apply {
                    appliesPreferredTrackTransform = true
                }.copyCGImageAtTime(CMTimeMakeWithSeconds(0.0, 600), null, null)
                    ?.let(UIImage::imageWithCGImage)
            } else {
                url.path?.let(UIImage::imageWithContentsOfFile)
            }
        }.getOrNull()
    }
