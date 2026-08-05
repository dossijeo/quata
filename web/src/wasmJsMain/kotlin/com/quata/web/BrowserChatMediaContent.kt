@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.core.config.QuataPublicBackendConfig
import com.quata.core.platform.PlatformFile
import com.quata.feature.chat.data.ChatAttachmentPublicUrlPolicy
import com.quata.feature.chat.presentation.chat.ChatAttachmentKind
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement

/** Browser decoder surfaces for the common Chat attachment frame and full-screen overlay. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun BrowserChatMediaContent(
    file: PlatformFile,
    kind: ChatAttachmentKind,
    viewer: Boolean,
    modifier: Modifier,
) {
    val source = file.reference.safeBrowserChatMediaUrl()
    if (source == null) {
        BrowserChatUnsupportedMediaContent(modifier)
        return
    }
    if (kind == ChatAttachmentKind.Image) {
        BrowserCanvasImage(
            url = source,
            contentDescription = file.displayName,
            contentScale = if (viewer) ContentScale.Fit else ContentScale.Crop,
            modifier = modifier,
        )
        return
    }
    if (kind != ChatAttachmentKind.Video) return

    val elementState = remember(source, viewer) { mutableStateOf<HTMLVideoElement?>(null) }
    DisposableEffect(source, viewer) {
        onDispose {
            elementState.value?.let { video ->
                video.pause()
                video.removeAttribute("src")
                video.load()
            }
            elementState.value = null
        }
    }
    WebElementView(
        factory = {
            (document.createElement("video") as HTMLVideoElement).apply {
                controls = viewer
                muted = !viewer
                autoplay = viewer
                preload = "metadata"
                setAttribute("playsinline", "true")
                style.width = "100%"
                style.height = "100%"
                style.objectFit = if (viewer) "contain" else "cover"
                elementState.value = this
            }
        },
        update = { video ->
            video.controls = viewer
            video.muted = !viewer
            video.style.objectFit = if (viewer) "contain" else "cover"
            if (video.src != source) video.src = source
        },
        modifier = modifier,
    )
}

@Composable
private fun BrowserChatUnsupportedMediaContent(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
        )
    }
}

internal fun String.safeBrowserChatMediaUrl(): String? {
    val value = trim()
    if (value.startsWith("blob:", ignoreCase = true)) return value
    return ChatAttachmentPublicUrlPolicy.canonicalUrlOrNull(QuataPublicBackendConfig.SUPABASE_URL, value)
}
