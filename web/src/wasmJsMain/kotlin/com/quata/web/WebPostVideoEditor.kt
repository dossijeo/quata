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
import com.quata.core.captions.templates.CaptionTemplateStyle
import com.quata.feature.postcomposer.videoeditor.CaptionStyleOption
import com.quata.feature.postcomposer.videoeditor.MaximumPostVideoEditorDurationMs
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorDialogContent
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorUiState
import com.quata.feature.postcomposer.videoeditor.postVideoEditorExportSpec
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropModeChange
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropPan
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCaptionsToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropZoomChange
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimEnd
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimStart
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
    var durationMs by remember(sourceReference) { mutableStateOf(MaximumPostVideoEditorDurationMs) }
    val videoAspectRatio = 9f / 16f
    val scope = rememberCoroutineScope()
    fun export() {
        if (state.isExporting) return
        state = state.copy(isExporting = true, exportProgress = 0.35f, error = null)
        val spec = postVideoEditorExportSpec(state, videoAspectRatio, durationMs)
        webPostVideoEditorRecordExportStarted()
        scope.launch {
            runCatching { webPostVideoEditorExportEdited(sourceReference, spec) }
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
            crop = { state = postVideoEditorStateAfterCropToggle(state) },
            captions = { state = postVideoEditorStateAfterCaptionsToggle(state) },
            export = { export() },
            dismiss = onDismiss,
        )
        onDispose { uninstall() }
    }

    PostVideoEditorDialogContent(
        state = state,
        onMutedChange = { state = state.copy(isMuted = it) },
        onPlayPause = { state = state.copy(isPlaying = !state.isPlaying) },
        onTrimStartChange = { state = postVideoEditorStateAfterTrimStart(state, it) },
        onTrimEndChange = { state = postVideoEditorStateAfterTrimEnd(state, it) },
        onCropToggle = { state = postVideoEditorStateAfterCropToggle(state) },
        onCaptionsToggle = { state = postVideoEditorStateAfterCaptionsToggle(state) },
        onReset = { state = PostVideoEditorUiState() },
        onDismiss = onDismiss,
        onExport = ::export,
        captionOptions = CaptionTemplateStyle.entries.map { CaptionStyleOption(it.name, it.name) },
        onCropModeChange = { state = postVideoEditorStateAfterCropModeChange(state, it, videoAspectRatio) },
        onCropZoomChange = { state = postVideoEditorStateAfterCropZoomChange(state, it, videoAspectRatio) },
        onCropPanChange = { dx, dy -> state = postVideoEditorStateAfterCropPan(state, dx, dy, videoAspectRatio) },
        onCaptionStyleChange = { state = state.copy(selectedCaptionStyleId = it, captionsEnabled = it != null) },
        onSeekChange = { state = state.copy(currentPositionFraction = it.coerceIn(0f, 1f)) },
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
                    if (!it.duration.isNaN() && it.duration.isFinite() && it.duration > 0.0) {
                        durationMs = (it.duration * 1000.0).toLong().coerceAtLeast(1L)
                    }
                    it.currentTime = (state.currentPositionFraction.coerceIn(0f, 1f) * durationMs) / 1000.0
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

internal suspend fun webPostVideoEditorExportEdited(
    reference: String,
    spec: com.quata.feature.postcomposer.videoeditor.PostVideoEditorExportSpec,
): String = suspendCoroutine { continuation ->
    webPostVideoEditorExportEditedJs(
        reference = reference,
        trimStartMs = spec.trimStartMs,
        trimEndMs = spec.trimEndMs,
        removeAudio = spec.removeAudio,
        cropLeft = spec.cropRect.left,
        cropTop = spec.cropRect.top,
        cropRight = spec.cropRect.right,
        cropBottom = spec.cropRect.bottom,
        captionStyle = spec.captionStyle?.name ?: "",
        outputWidth = spec.outputWidth,
        outputHeight = spec.outputHeight,
        continuation::resume,
    ) { message ->
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

private fun webPostVideoEditorExportEditedJs(
    reference: String,
    trimStartMs: Long,
    trimEndMs: Long,
    removeAudio: Boolean,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    captionStyle: String,
    outputWidth: Int,
    outputHeight: Int,
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
        const sourceUrlPromise = value.startsWith('blob:') || value.startsWith('data:')
          ? Promise.resolve(value)
          : fetch(value).then(response => {
              if (!response.ok) throw Error('web_post_video_editor_source_' + response.status);
              return response.blob();
            }).then(blob => globalThis.URL.createObjectURL(blob));
        sourceUrlPromise.then(sourceUrl => new Promise((resolve, reject) => {
          const video = globalThis.document.createElement('video');
          video.crossOrigin = 'anonymous';
          video.muted = Boolean(removeAudio);
          video.playsInline = true;
          video.preload = 'auto';
          video.src = sourceUrl;
          video.onloadedmetadata = () => resolve(video);
          video.onerror = () => reject(Error('web_post_video_editor_video_load_failed'));
        })).then(video => new Promise((resolve, reject) => {
          const canvas = globalThis.document.createElement('canvas');
          canvas.width = Math.max(2, Number(outputWidth) || 1080);
          canvas.height = Math.max(2, Number(outputHeight) || 1920);
          const context = canvas.getContext('2d');
          if (!context) throw Error('web_post_video_editor_canvas_context_unavailable');
          const fps = 30;
          const stream = canvas.captureStream?.(fps);
          if (!stream || typeof globalThis.MediaRecorder !== 'function') {
            throw Error('web_post_video_editor_media_recorder_unavailable');
          }
          if (!removeAudio && typeof video.captureStream === 'function') {
            try {
              const mediaStream = video.captureStream();
              mediaStream?.getAudioTracks?.().forEach(track => stream.addTrack(track));
            } catch (_) {}
          }
          const chunks = [];
          const mimeType = globalThis.MediaRecorder.isTypeSupported?.('video/webm;codecs=vp9')
            ? 'video/webm;codecs=vp9'
            : 'video/webm';
          const recorder = new globalThis.MediaRecorder(stream, { mimeType });
          recorder.ondataavailable = event => { if (event.data && event.data.size) chunks.push(event.data); };
          recorder.onerror = event => reject(Error(event?.error?.message || 'web_post_video_editor_recorder_failed'));
          recorder.onstop = () => {
            const blob = new Blob(chunks, { type: mimeType });
            if (!blob.size) reject(Error('web_post_video_editor_empty_export'));
            else resolve(globalThis.URL.createObjectURL(blob));
          };
          const startMs = Math.max(0, Number(trimStartMs) || 0);
          const endMs = Math.max(startMs + 500, Number(trimEndMs) || (video.duration * 1000));
          const durationMs = endMs - startMs;
          const crop = {
            left: Math.max(0, Math.min(1, Number(cropLeft) || 0)),
            top: Math.max(0, Math.min(1, Number(cropTop) || 0)),
            right: Math.max(0, Math.min(1, Number(cropRight) || 1)),
            bottom: Math.max(0, Math.min(1, Number(cropBottom) || 1)),
          };
          const caption = String(captionStyle || '');
          const sourceWidth = Math.max(1, video.videoWidth || canvas.width);
          const sourceHeight = Math.max(1, video.videoHeight || canvas.height);
          const cropWidth = Math.max(1, (crop.right - crop.left) * sourceWidth);
          const cropHeight = Math.max(1, (crop.bottom - crop.top) * sourceHeight);
          function drawFrame() {
            context.fillStyle = '#000000';
            context.fillRect(0, 0, canvas.width, canvas.height);
            context.drawImage(
              video,
              crop.left * sourceWidth,
              crop.top * sourceHeight,
              cropWidth,
              cropHeight,
              0,
              0,
              canvas.width,
              canvas.height
            );
            if (caption) {
              context.fillStyle = 'rgba(0,0,0,0.72)';
              context.fillRect(canvas.width * 0.08, canvas.height * 0.72, canvas.width * 0.84, canvas.height * 0.095);
              context.fillStyle = '#ffffff';
              context.textAlign = 'center';
              context.font = `${Math.round(canvas.width * 0.056)}px sans-serif`;
              context.fillText(caption.toUpperCase(), canvas.width / 2, canvas.height * 0.78);
            }
          }
          video.onseeked = () => {
            recorder.start(250);
            video.play?.().catch(() => {});
            const startedAt = performance.now();
            const timer = setInterval(() => {
              drawFrame();
              if ((performance.now() - startedAt) >= durationMs || (video.currentTime * 1000) >= endMs) {
                clearInterval(timer);
                video.pause?.();
                recorder.stop();
              }
            }, 1000 / fps);
          };
          video.currentTime = startMs / 1000;
        })).then(onSuccess).catch(error => onFailure(String(error?.message || error || 'web_post_video_editor_export_failed').slice(0, 160)));
      } catch (error) {
        onFailure(String(error?.message || error || 'web_post_video_editor_export_failed').slice(0, 160));
      }
    })()
    """,
)
