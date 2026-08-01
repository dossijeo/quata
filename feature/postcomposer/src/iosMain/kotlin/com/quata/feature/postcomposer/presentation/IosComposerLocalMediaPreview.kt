package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFURLCreateWithFileSystemPath
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFNumberIntType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFURLPOSIXPathStyle
import platform.Foundation.NSURL
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentMode

/** A truthful local-only media preview. It never asks UIKit to resolve a remote URL. */
@Composable
fun IosComposerLocalImagePreview(file: PlatformFile, modifier: Modifier = Modifier) {
    var preview by remember(file.reference) { mutableStateOf<IosComposerImagePreview>(IosComposerImagePreview.Loading) }
    LaunchedEffect(file.reference) { preview = iosComposerPreviewImageCache.acquire(file) }
    DisposableEffect(file.reference) {
        onDispose { iosComposerPreviewImageCache.release(file) }
    }
    when (val value = preview) {
        IosComposerImagePreview.Loading -> Text("Loading local image preview...")
        is IosComposerImagePreview.Image -> {
            UIKitView(
                factory = {
                    UIImageView().apply {
                        contentMode = UIViewContentMode.UIViewContentModeScaleAspectFit
                        clipsToBounds = true
                        image = value.value
                    }
                },
                update = { it.image = value.value },
                modifier = modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
            )
        }
        IosComposerImagePreview.Unavailable -> {
            // A selected file is not evidence that UIKit can decode it. Keep that distinction visible.
            Text("Local image preview unavailable")
        }
    }
}

internal sealed interface IosComposerImagePreview {
    data object Loading : IosComposerImagePreview
    data class Image(val value: UIImage) : IosComposerImagePreview
    data object Unavailable : IosComposerImagePreview
}

/**
 * Bounded decode cache for the temporary files provided by iOS adapters. Loading happens from a
 * [LaunchedEffect], never from the composable body; the cache holds one image per active path.
 */
private val iosComposerPreviewImageCache = IosComposerPreviewImageCache()

internal class IosComposerPreviewImageCache(
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val decoder: suspend (PlatformFile) -> IosComposerImagePreview = { file ->
        file.localThumbnailOrNull()?.let(IosComposerImagePreview::Image) ?: IosComposerImagePreview.Unavailable
    },
) {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + decodeDispatcher)
    private val entries = mutableMapOf<String, Entry>()

    /**
     * Retains one reference before waiting. Decoding lives in [scope], rather than the caller's
     * UI effect, so a replaced/disposed composition cannot race a new consumer into a stale cache.
     */
    suspend fun acquire(file: PlatformFile): IosComposerImagePreview {
        val key = file.reference
        val deferred = lock.withLock {
            when (val entry = entries[key]) {
                is Entry.Ready -> {
                    entry.references += 1
                    return entry.preview
                }
                is Entry.Loading -> {
                    entry.references += 1
                    entry.result
                }
                null -> startLoad(key, file)
            }
        }
        // LaunchedEffect resumes on its main dispatcher after await; only then does it update UI.
        return deferred.await()
    }

    fun release(file: PlatformFile) {
        val key = file.reference
        scope.launch {
            lock.withLock {
                when (val entry = entries[key]) {
                    is Entry.Ready -> {
                        entry.references -= 1
                        if (entry.references <= 0) entries.remove(key)
                    }
                    is Entry.Loading -> {
                        entry.references -= 1
                        // Keep an in-flight entry so other consumers can await it. The completion
                        // handler removes it when its last reference has already been released.
                        if (entry.references <= 0 && entry.result.isCompleted) entries.remove(key)
                    }
                    null -> Unit
                }
            }
        }
    }

    private fun startLoad(key: String, file: PlatformFile): CompletableDeferred<IosComposerImagePreview> {
        val result = CompletableDeferred<IosComposerImagePreview>()
        entries[key] = Entry.Loading(references = 1, result = result)
        scope.launch {
            val preview = withContext(decodeDispatcher) {
                runCatching { decoder(file) }.getOrDefault(IosComposerImagePreview.Unavailable)
            }
            lock.withLock {
                val loading = entries[key] as? Entry.Loading
                if (loading?.result === result) {
                    if (loading.references <= 0) entries.remove(key)
                    else entries[key] = Entry.Ready(loading.references, preview)
                }
                result.complete(preview)
            }
        }
        return result
    }

    private sealed interface Entry {
        class Loading(var references: Int, val result: CompletableDeferred<IosComposerImagePreview>) : Entry
        class Ready(var references: Int, val preview: IosComposerImagePreview) : Entry
    }

    internal suspend fun retainedEntryCount(): Int = lock.withLock { entries.size }
}

internal sealed interface IosComposerVideoPreview {
    data object Generating : IosComposerVideoPreview
    data class Thumbnail(val file: PlatformFile) : IosComposerVideoPreview
    data class Unavailable(val reason: String) : IosComposerVideoPreview
}

/** Codec/decoder errors are explicit; they are not represented as a fake video card. */
internal fun PlatformResult<PlatformFile>.toIosComposerVideoPreview(): IosComposerVideoPreview = when (this) {
    is PlatformResult.Success -> IosComposerVideoPreview.Thumbnail(value)
    is PlatformResult.Failure -> IosComposerVideoPreview.Unavailable(reason ?: "video_thumbnail_unavailable")
    PlatformResult.Cancelled -> IosComposerVideoPreview.Unavailable("video_thumbnail_cancelled")
    PlatformResult.Unsupported -> IosComposerVideoPreview.Unavailable("video_thumbnail_unsupported")
}

/** Releases only a thumbnail generated by [IosVideoThumbnailService], never an arbitrary file. */
@OptIn(ExperimentalForeignApi::class)
fun releaseIosComposerVideoThumbnail(file: PlatformFile): Boolean {
    val reference = file.reference.trim()
    val url = when {
        reference.startsWith("file://") -> NSURL(string = reference)
        reference.startsWith("/") -> NSURL.fileURLWithPath(reference)
        else -> null
    }?.takeIf { it.isFileURL() } ?: return false
    val path = url.path ?: return false
    if (!isOwnedIosComposerVideoThumbnailPath(path, NSTemporaryDirectory())) return false
    return NSFileManager.defaultManager.removeItemAtURL(url, error = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun PlatformFile.localThumbnailOrNull(): UIImage? {
    val value = reference.trim()
    val url = when {
        value.startsWith("file://") -> NSURL(string = value)
        value.startsWith("/") -> NSURL.fileURLWithPath(value)
        else -> null
    }?.takeIf { it.isFileURL() } ?: return null
    val path = url.path ?: return null
    val pathRef = CFStringCreateWithCString(null, path, kCFStringEncodingUTF8) ?: return null
    val fileUrl = CFURLCreateWithFileSystemPath(null, pathRef, kCFURLPOSIXPathStyle, false)
    CFRelease(pathRef)
    fileUrl ?: return null
    return try {
        val source = CGImageSourceCreateWithURL(fileUrl, null) ?: return null
        try {
            withThumbnailOptions(640) { options ->
                val image = CGImageSourceCreateThumbnailAtIndex(source, 0u, options) ?: return@withThumbnailOptions null
                try {
                    UIImage.imageWithCGImage(image)
                } finally {
                    CFRelease(image)
                }
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(fileUrl)
    }
}

@OptIn(ExperimentalForeignApi::class)
private inline fun <T> withThumbnailOptions(maxPixelSize: Int, block: (CFDictionaryRef) -> T): T = memScoped {
    val maxPixels = alloc<IntVar>().apply { value = maxPixelSize }
    val maxPixelsRef = CFNumberCreate(null, kCFNumberIntType, maxPixels.ptr)
        ?: error("Core Foundation could not create the thumbnail size")
    val options = CFDictionaryCreateMutable(null, 3, null, null)
        ?: error("Core Foundation could not create thumbnail options")
    try {
        CFDictionaryAddValue(options, kCGImageSourceCreateThumbnailFromImageAlways.toCfPointer(), kCFBooleanTrue.toCfPointer())
        CFDictionaryAddValue(options, kCGImageSourceCreateThumbnailWithTransform.toCfPointer(), kCFBooleanTrue.toCfPointer())
        CFDictionaryAddValue(options, kCGImageSourceThumbnailMaxPixelSize.toCfPointer(), maxPixelsRef.toCfPointer())
        block(options)
    } finally {
        CFRelease(options)
        CFRelease(maxPixelsRef)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun Any?.toCfPointer(): CPointer<COpaque> = (this as? CPointer<*>)
    ?.reinterpret()
    ?: error("Thumbnail options must be Core Foundation objects")
