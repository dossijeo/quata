@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorDialogContent
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorUiState
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLVideoElement
import kotlinx.browser.document
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WebPostVideoEditor(
    sourceReference: String,
    onDismiss: () -> Unit,
    onEdited: (String) -> Unit,
) {
    var state by remember(sourceReference) { mutableStateOf(PostVideoEditorUiState()) }
    val scope = rememberCoroutineScope()
    fun export() {
        if (state.isExporting) return
        state = state.copy(isExporting = true, exportProgress = 0.35f, error = null)
        webPostVideoEditorRecordExportStarted()
        scope.launch {
            runCatching { webPostVideoEditorExportCopy(sourceReference) }
                .onSuccess {
                    state = state.copy(isExporting = false, exportProgress = 1f)
                    webPostVideoEditorRecordExportSuccess(it)
                    onEdited(it)
                }
                .onFailure {
                    val message = it.message ?: "web_post_video_editor_export_failed"
                    state = state.copy(isExporting = false, error = message)
                    webPostVideoEditorRecordExportFailure(message)
                }
        }
    }
    DisposableEffect(sourceReference, state, onDismiss, onEdited) {
        val uninstall = installWebPostVideoEditorE2eBridge(
            mute = { state = state.copy(isMuted = !state.isMuted) },
            playPause = { state = state.copy(isPlaying = !state.isPlaying) },
            crop = { state = state.copy(cropEnabled = !state.cropEnabled) },
            captions = { state = state.copy(captionsEnabled = !state.captionsEnabled) },
            export = { export() },
            dismiss = onDismiss,
        )
        onDispose { uninstall() }
    }

    PostVideoEditorDialogContent(
        state = state,
        onMutedChange = { state = state.copy(isMuted = it) },
        onPlayPause = { state = state.copy(isPlaying = !state.isPlaying) },
        onTrimStartChange = { state = state.copy(trimStartFraction = it) },
        onTrimEndChange = { state = state.copy(trimEndFraction = it) },
        onCropToggle = { state = state.copy(cropEnabled = !state.cropEnabled) },
        onCaptionsToggle = { state = state.copy(captionsEnabled = !state.captionsEnabled) },
        onReset = { state = PostVideoEditorUiState() },
        onDismiss = onDismiss,
        onExport = ::export,
        preview = { modifier ->
            WebElementView(
                factory = {
                    (document.createElement("video") as HTMLVideoElement).apply {
                        controls = false
                        muted = state.isMuted
                        loop = true
                        preload = "metadata"
                        style.width = "100%"
                        style.height = "100%"
                        style.objectFit = "contain"
                    }
                },
                update = {
                    it.src = sourceReference
                    it.muted = state.isMuted
                    webPostVideoEditorApplyPlayback(it, state.isPlaying)
                },
                modifier = modifier,
            )
        },
    )
}

internal fun installWebPostVideoEditorE2eBridge(
    mute: () -> Unit,
    playPause: () -> Unit,
    crop: () -> Unit,
    captions: () -> Unit,
    export: () -> Unit,
    dismiss: () -> Unit,
): () -> Unit = installPostVideoEditorBridgeWhenAllowed(mute, playPause, crop, captions, export, dismiss)

@JsFun(
    """(mute, playPause, crop, captions, exportVideo, dismiss) => {
      const local = location?.hostname === 'localhost' || location?.hostname === '127.0.0.1';
      const params = new URLSearchParams(location?.search || '');
      const optedIn = params.get('quata-post-video-editor-e2e') === '1' ||
        params.get('quata-post-publish-e2e') === '1' ||
        globalThis.sessionStorage?.getItem('quata.post_publish.e2e') === '1';
      if (!local || !optedIn) return () => {};
      const bridge = Object.freeze({
        version: 1,
        mute: () => mute(),
        playPause: () => playPause(),
        crop: () => crop(),
        captions: () => captions(),
        export: () => exportVideo(),
        dismiss: () => dismiss(),
      });
      globalThis.__quataPostVideoEditorE2eProduct = bridge;
      globalThis.document?.documentElement?.setAttribute('data-quata-post-video-editor-e2e', 'ready');
      return () => {
        if (globalThis.__quataPostVideoEditorE2eProduct === bridge) delete globalThis.__quataPostVideoEditorE2eProduct;
        globalThis.document?.documentElement?.removeAttribute('data-quata-post-video-editor-e2e');
      };
    }""",
)
private external fun installPostVideoEditorBridgeWhenAllowed(
    mute: () -> Unit,
    playPause: () -> Unit,
    crop: () -> Unit,
    captions: () -> Unit,
    export: () -> Unit,
    dismiss: () -> Unit,
): () -> Unit

private fun webPostVideoEditorApplyPlayback(video: HTMLVideoElement, shouldPlay: Boolean): Unit = js(
    """(() => {
        if (shouldPlay) {
            const result = video.play?.();
            if (result && typeof result.catch === 'function') result.catch(() => {});
        } else {
            video.pause?.();
        }
    })()""",
)

internal suspend fun webPostVideoEditorExportCopy(reference: String): String = suspendCoroutine { continuation ->
    webPostVideoEditorExportCopyJs(reference, continuation::resume) { message ->
        continuation.resumeWith(Result.failure(IllegalStateException(message)))
    }
}

private fun webPostVideoEditorRecordExportStarted(): Unit = js(
    """(() => { globalThis.__quataPostVideoEditorExport = { status: 'started' }; })()""",
)

private fun webPostVideoEditorRecordExportSuccess(reference: String): Unit = js(
    """(() => { globalThis.__quataPostVideoEditorExport = { status: 'success', reference: String(reference).slice(0, 80) }; })()""",
)

private fun webPostVideoEditorRecordExportFailure(message: String): Unit = js(
    """(() => { globalThis.__quataPostVideoEditorExport = { status: 'failed', message: String(message).slice(0, 160) }; })()""",
)

private fun webPostVideoEditorExportCopyJs(
    reference: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      try {
        if (!globalThis.URL?.createObjectURL || typeof fetch !== 'function') {
          onFailure('web_post_video_editor_blob_unsupported'); return;
        }
        const value = String(reference || '');
        const asBlob = value.startsWith('data:') || value.startsWith('blob:')
          ? fetch(value).then(response => {
              if (!response.ok) throw Error('web_post_video_editor_source_' + response.status);
              return response.blob();
            })
          : fetch(value).then(response => {
              if (!response.ok) throw Error('web_post_video_editor_source_' + response.status);
              return response.blob();
            });
        asBlob.then(blob => {
          if (!blob || !blob.size) { onFailure('web_post_video_editor_empty_blob'); return; }
          const output = new Blob([blob], { type: blob.type || 'video/mp4' });
          onSuccess(globalThis.URL.createObjectURL(output));
        }).catch(error => onFailure(String(error?.message || error || 'web_post_video_editor_export_failed').slice(0, 160)));
      } catch (error) {
        onFailure(String(error?.message || error || 'web_post_video_editor_export_failed').slice(0, 160));
      }
    })()
    """,
)
