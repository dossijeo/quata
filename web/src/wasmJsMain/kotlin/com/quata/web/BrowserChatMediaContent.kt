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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.js.JsString
import kotlin.js.toJsString
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

    var playableSource by remember(source) { mutableStateOf(source.takeIf { it.startsWith("blob:", ignoreCase = true) }) }
    var loadFailed by remember(source) { mutableStateOf(false) }
    DisposableEffect(source) {
        val cleanup = if (source.startsWith("blob:", ignoreCase = true)) {
            playableSource = source
            {}
        } else {
            resolveBrowserChatVideoSource(
                source.toJsString(),
                onSuccess = { resolved -> playableSource = resolved.toString(); loadFailed = false },
                onFailure = { loadFailed = true },
            )
        }
        onDispose {
            cleanup()
            playableSource
                ?.takeIf { it.startsWith("blob:", ignoreCase = true) && it != source }
                ?.let { revokeBrowserChatVideoSource(it.toJsString()) }
            playableSource = null
        }
    }
    val videoSource = playableSource
    if (videoSource == null) {
        BrowserChatUnsupportedMediaContent(modifier)
        return
    }
    if (loadFailed) {
        BrowserChatUnsupportedMediaContent(modifier)
        return
    }

    val elementState = remember(videoSource, viewer) { mutableStateOf<HTMLVideoElement?>(null) }
    DisposableEffect(videoSource, viewer) {
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
            if (video.src != videoSource) video.src = videoSource
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

@JsFun(
    """(source, onSuccess, onFailure) => {
      if (!/^https?:/i.test(source) || typeof globalThis.fetch !== 'function' || !globalThis.URL?.createObjectURL) {
        onFailure('web_chat_video_reference_unsupported');
        return () => {};
      }
      const controller = typeof globalThis.AbortController === 'function' ? new globalThis.AbortController() : null;
      let objectUrl = null;
      let disposed = false;
      globalThis.fetch(source, { credentials: 'omit', cache: 'no-store', redirect: 'error', ...(controller ? { signal: controller.signal } : {}) })
        .then(async (response) => {
          if (!response.ok) throw new Error('web_chat_video_http_' + response.status);
          const blob = await response.blob();
          if (!blob || !Number.isFinite(blob.size) || blob.size <= 0 || blob.size > 50 * 1024 * 1024) throw new Error('web_chat_video_blob_empty');
          const nextObjectUrl = globalThis.URL.createObjectURL(blob);
          if (disposed) {
            globalThis.URL.revokeObjectURL(nextObjectUrl);
            return;
          }
          objectUrl = nextObjectUrl;
          onSuccess(nextObjectUrl);
        })
        .catch((error) => {
          if (!disposed && error?.name !== 'AbortError') onFailure(error?.message || 'web_chat_video_load_failed');
        });
      return () => {
        disposed = true;
        if (controller) controller.abort();
        if (objectUrl) {
          globalThis.URL.revokeObjectURL(objectUrl);
          objectUrl = null;
        }
      };
    }""",
)
private external fun resolveBrowserChatVideoSource(
    source: JsString,
    onSuccess: (JsString) -> Unit,
    onFailure: (JsString) -> Unit,
): () -> Unit

@JsFun("(source) => { if (typeof source === 'string' && /^blob:/i.test(source) && globalThis.URL?.revokeObjectURL) globalThis.URL.revokeObjectURL(source); }")
private external fun revokeBrowserChatVideoSource(source: JsString)
