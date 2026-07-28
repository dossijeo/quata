package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import platform.Foundation.NSURL
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIViewContentModeScaleAspectFit

/** A truthful local-only media preview. It never asks UIKit to resolve a remote URL. */
@Composable
internal fun IosComposerLocalImagePreview(file: PlatformFile, modifier: Modifier = Modifier) {
    val image = file.localImageOrNull()
    if (image != null) {
        UIKitView(
            factory = {
                UIImageView().apply {
                    contentMode = UIViewContentModeScaleAspectFit
                    clipsToBounds = true
                    this.image = image
                }
            },
            update = { it.image = image },
            modifier = modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
        )
    } else {
        // A selected file is not evidence that UIKit can decode it. Keep that distinction visible.
        Text("Local image preview unavailable")
    }
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

private fun PlatformFile.localImageOrNull(): UIImage? {
    val value = reference.trim()
    val path = when {
        value.startsWith("file://") -> NSURL(string = value).path
        value.startsWith("/") -> value
        else -> null
    } ?: return null
    return UIImage.imageWithContentsOfFile(path)
}
