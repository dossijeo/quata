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
import com.quata.feature.postcomposer.videoeditor.VideoCropMode
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
    var videoAspectRatio by remember(sourceReference) { mutableStateOf(9f / 16f) }
    val scope = rememberCoroutineScope()
    fun export() {
        if (state.isExporting) return
        state = state.copy(isExporting = true, exportProgress = 0.35f, error = null)
        webPostVideoEditorRecordExportStarted()
        scope.launch {
            val freshMetadata = runCatching { webPostVideoEditorReadMetadata(sourceReference) }.getOrNull()
            val exportDurationMs = freshMetadata?.durationMs?.also { durationMs = it } ?: durationMs
            val exportAspectRatio = freshMetadata?.aspectRatio?.also { videoAspectRatio = it } ?: videoAspectRatio
            val spec = postVideoEditorExportSpec(state, exportAspectRatio, exportDurationMs)
            runCatching { webPostVideoEditorExportEdited(sourceReference, spec) }
                .onSuccess {
                    state = state.copy(isExporting = false, exportProgress = 1f)
                    webPostVideoEditorRecordExportSuccess(
                        reference = it,
                        trimStartMs = spec.trimStartMs,
                        trimEndMs = spec.trimEndMs,
                        sourceDurationMs = spec.sourceDurationMs,
                        removeAudio = spec.removeAudio,
                        cropLeft = spec.cropRect.left,
                        cropTop = spec.cropRect.top,
                        cropRight = spec.cropRect.right,
                        cropBottom = spec.cropRect.bottom,
                        backgroundCropLeft = spec.backgroundCropRect?.left ?: 0f,
                        backgroundCropTop = spec.backgroundCropRect?.top ?: 0f,
                        backgroundCropRight = spec.backgroundCropRect?.right ?: 1f,
                        backgroundCropBottom = spec.backgroundCropRect?.bottom ?: 1f,
                        captionStyle = spec.captionStyle?.name ?: "",
                        captionText = spec.captionText ?: "",
                        outputWidth = spec.outputWidth,
                        outputHeight = spec.outputHeight,
                    )
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
            trimStart = { state = postVideoEditorStateAfterTrimStart(state, it) },
            trimEnd = { state = postVideoEditorStateAfterTrimEnd(state, it) },
            crop = { state = postVideoEditorStateAfterCropToggle(state) },
            cropMode = { mode ->
                VideoCropMode.entries.firstOrNull { it.name == mode }?.let {
                    state = postVideoEditorStateAfterCropModeChange(state, it, videoAspectRatio)
                }
            },
            cropZoom = { state = postVideoEditorStateAfterCropZoomChange(state, it, videoAspectRatio) },
            cropPan = { dx, dy -> state = postVideoEditorStateAfterCropPan(state, dx, dy, videoAspectRatio) },
            captions = { state = postVideoEditorStateAfterCaptionsToggle(state) },
            captionStyle = { state = state.copy(selectedCaptionStyleId = it, captionsEnabled = it != null) },
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
                    if (it.videoWidth > 0 && it.videoHeight > 0) {
                        videoAspectRatio = it.videoWidth.toFloat() / it.videoHeight.toFloat()
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
    trimStart: (Float) -> Unit,
    trimEnd: (Float) -> Unit,
    crop: () -> Unit,
    cropMode: (String) -> Unit,
    cropZoom: (Float) -> Unit,
    cropPan: (Float, Float) -> Unit,
    captions: () -> Unit,
    captionStyle: (String?) -> Unit,
    export: () -> Unit,
    dismiss: () -> Unit,
): () -> Unit = installPostVideoEditorBridgeWhenAllowed(
    mute,
    playPause,
    trimStart,
    trimEnd,
    crop,
    cropMode,
    cropZoom,
    cropPan,
    captions,
    captionStyle,
    export,
    dismiss,
)

@JsFun(
    """(mute, playPause, trimStart, trimEnd, crop, cropMode, cropZoom, cropPan, captions, captionStyle, exportVideo, dismiss) => {
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
        trimStart: value => trimStart(Number(value)),
        trimEnd: value => trimEnd(Number(value)),
        crop: () => crop(),
        cropMode: value => cropMode(String(value || '')),
        cropZoom: value => cropZoom(Number(value)),
        cropPan: (dx, dy) => cropPan(Number(dx), Number(dy)),
        captions: () => captions(),
        captionStyle: value => captionStyle(value == null || value === '' ? null : String(value)),
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
    trimStart: (Float) -> Unit,
    trimEnd: (Float) -> Unit,
    crop: () -> Unit,
    cropMode: (String) -> Unit,
    cropZoom: (Float) -> Unit,
    cropPan: (Float, Float) -> Unit,
    captions: () -> Unit,
    captionStyle: (String?) -> Unit,
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
        sourceDurationMs = spec.sourceDurationMs,
        removeAudio = spec.removeAudio,
        cropLeft = spec.cropRect.left,
        cropTop = spec.cropRect.top,
        cropRight = spec.cropRect.right,
        cropBottom = spec.cropRect.bottom,
        backgroundCropLeft = spec.backgroundCropRect?.left ?: 0f,
        backgroundCropTop = spec.backgroundCropRect?.top ?: 0f,
        backgroundCropRight = spec.backgroundCropRect?.right ?: 1f,
        backgroundCropBottom = spec.backgroundCropRect?.bottom ?: 1f,
        captionStyle = spec.captionStyle?.name ?: "",
        captionText = spec.captionText ?: "",
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

private fun webPostVideoEditorRecordExportSuccess(
    reference: String,
    trimStartMs: Long,
    trimEndMs: Long,
    sourceDurationMs: Long,
    removeAudio: Boolean,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    backgroundCropLeft: Float,
    backgroundCropTop: Float,
    backgroundCropRight: Float,
    backgroundCropBottom: Float,
    captionStyle: String,
    captionText: String,
    outputWidth: Int,
    outputHeight: Int,
): Unit = js(
    """(() => {
        const native = globalThis.__quataPostVideoEditorExportNative || {};
        const cropChanged = Math.abs(Number(cropLeft)) > 0.001 ||
          Math.abs(Number(cropTop)) > 0.001 ||
          Math.abs(Number(cropRight) - 1) > 0.001 ||
          Math.abs(Number(cropBottom) - 1) > 0.001;
        globalThis.__quataPostVideoEditorExport = {
          status: 'success',
          reference: String(reference),
          output: {
            mimeType: String(native.mimeType || ''),
            size: Number(native.size || 0),
            outputWidth: Number(outputWidth),
            outputHeight: Number(outputHeight),
            actualSourceDurationMs: Number(native.actualSourceDurationMs || 0),
            effectiveTrimStartMs: Number(native.effectiveTrimStartMs || 0),
            effectiveTrimEndMs: Number(native.effectiveTrimEndMs || 0),
            effectiveDurationMs: Number(native.effectiveDurationMs || 0),
          },
          spec: {
            trimStartMs: Number(trimStartMs),
            trimEndMs: Number(trimEndMs),
            sourceDurationMs: Number(sourceDurationMs),
            removeAudio: Boolean(removeAudio),
            cropLeft: Number(cropLeft),
            cropTop: Number(cropTop),
            cropRight: Number(cropRight),
            cropBottom: Number(cropBottom),
            backgroundCropLeft: Number(backgroundCropLeft),
            backgroundCropTop: Number(backgroundCropTop),
            backgroundCropRight: Number(backgroundCropRight),
            backgroundCropBottom: Number(backgroundCropBottom),
            captionStyle: String(captionStyle || ''),
            captionText: String(captionText || ''),
          },
          operations: {
            trim: Number(trimStartMs) > 0 || Number(trimEndMs) < Number(sourceDurationMs),
            mute: Boolean(removeAudio),
            crop: cropChanged,
            captions: String(captionStyle || '').length > 0 && String(captionText || '').length > 0,
          },
        };
      })()""",
)

private fun webPostVideoEditorRecordExportFailure(message: String): Unit = js(
    """(() => { globalThis.__quataPostVideoEditorExport = { status: 'failed', message: String(message).slice(0, 160) }; })()""",
)

private fun webPostVideoEditorExportEditedJs(
    reference: String,
    trimStartMs: Long,
    trimEndMs: Long,
    sourceDurationMs: Long,
    removeAudio: Boolean,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    backgroundCropLeft: Float,
    backgroundCropTop: Float,
    backgroundCropRight: Float,
    backgroundCropBottom: Float,
    captionStyle: String,
    captionText: String,
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
            else {
              globalThis.__quataPostVideoEditorExportNative = {
                mimeType,
                size: blob.size,
                outputWidth: canvas.width,
                outputHeight: canvas.height,
                actualSourceDurationMs: actualDurationMs,
                effectiveTrimStartMs: startMs,
                effectiveTrimEndMs: endMs,
                effectiveDurationMs: durationMs,
              };
              resolve(globalThis.URL.createObjectURL(blob));
            }
          };
          const actualDurationMs = Math.max(500, (Number(video.duration) || 0) * 1000);
          const hintedDurationMs = Math.max(1, Number(sourceDurationMs) || actualDurationMs);
          const scale = actualDurationMs > 0 && hintedDurationMs > actualDurationMs * 1.5
            ? actualDurationMs / hintedDurationMs
            : 1;
          const startMs = Math.min(
            Math.max(0, Number(trimStartMs) || 0) * scale,
            Math.max(0, actualDurationMs - 500)
          );
          const requestedEndMs = Math.max(startMs + 500, (Number(trimEndMs) || actualDurationMs) * scale);
          const endMs = Math.min(actualDurationMs, requestedEndMs);
          const durationMs = endMs - startMs;
          const crop = {
            left: Math.max(0, Math.min(1, Number(cropLeft) || 0)),
            top: Math.max(0, Math.min(1, Number(cropTop) || 0)),
            right: Math.max(0, Math.min(1, Number(cropRight) || 1)),
            bottom: Math.max(0, Math.min(1, Number(cropBottom) || 1)),
          };
          const backgroundCrop = {
            left: Math.max(0, Math.min(1, Number(backgroundCropLeft) || 0)),
            top: Math.max(0, Math.min(1, Number(backgroundCropTop) || 0)),
            right: Math.max(0, Math.min(1, Number(backgroundCropRight) || 1)),
            bottom: Math.max(0, Math.min(1, Number(backgroundCropBottom) || 1)),
          };
          const caption = String(captionStyle || '');
          const captionWords = String(captionText || '').trim();
          if (caption && !captionWords) throw Error('web_post_video_editor_caption_transcript_missing');
          const sourceWidth = Math.max(1, video.videoWidth || canvas.width);
          const sourceHeight = Math.max(1, video.videoHeight || canvas.height);
          function drawCropFit(rect) {
            const cropWidth = Math.max(1, (rect.right - rect.left) * sourceWidth);
            const cropHeight = Math.max(1, (rect.bottom - rect.top) * sourceHeight);
            const scale = Math.min(canvas.width / cropWidth, canvas.height / cropHeight);
            const drawWidth = cropWidth * scale;
            const drawHeight = cropHeight * scale;
            context.drawImage(
              video,
              rect.left * sourceWidth,
              rect.top * sourceHeight,
              cropWidth,
              cropHeight,
              (canvas.width - drawWidth) / 2,
              (canvas.height - drawHeight) / 2,
              drawWidth,
              drawHeight
            );
          }
          function drawCropFill(rect) {
            const cropWidth = Math.max(1, (rect.right - rect.left) * sourceWidth);
            const cropHeight = Math.max(1, (rect.bottom - rect.top) * sourceHeight);
            const scale = Math.max(canvas.width / cropWidth, canvas.height / cropHeight);
            const drawWidth = cropWidth * scale;
            const drawHeight = cropHeight * scale;
            context.drawImage(
              video,
              rect.left * sourceWidth,
              rect.top * sourceHeight,
              cropWidth,
              cropHeight,
              (canvas.width - drawWidth) / 2,
              (canvas.height - drawHeight) / 2,
              drawWidth,
              drawHeight
            );
          }
          function drawFrame() {
            context.fillStyle = '#000000';
            context.fillRect(0, 0, canvas.width, canvas.height);
            drawCropFill(backgroundCrop);
            context.fillStyle = 'rgba(0,0,0,0.24)';
            context.fillRect(0, 0, canvas.width, canvas.height);
            drawCropFit(crop);
            if (caption) {
              context.fillStyle = 'rgba(0,0,0,0.72)';
              context.fillRect(canvas.width * 0.08, canvas.height * 0.72, canvas.width * 0.84, canvas.height * 0.095);
              context.fillStyle = '#ffffff';
              context.textAlign = 'center';
              context.font = Math.round(canvas.width * 0.056) + 'px sans-serif';
              context.fillText(captionWords.toUpperCase(), canvas.width / 2, canvas.height * 0.78);
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

private data class WebPostVideoEditorMetadata(
    val durationMs: Long,
    val aspectRatio: Float,
)

private suspend fun webPostVideoEditorReadMetadata(reference: String): WebPostVideoEditorMetadata =
    suspendCoroutine { continuation ->
        webPostVideoEditorReadMetadataJs(
            reference = reference,
            onSuccess = { durationMs, width, height ->
                val safeDuration = durationMs.toLong().coerceAtLeast(1L)
                val safeAspectRatio = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 9f / 16f
                continuation.resume(WebPostVideoEditorMetadata(safeDuration, safeAspectRatio))
            },
            onFailure = { continuation.resume(WebPostVideoEditorMetadata(MaximumPostVideoEditorDurationMs, 9f / 16f)) },
        )
    }

private fun webPostVideoEditorReadMetadataJs(
    reference: String,
    onSuccess: (Double, Int, Int) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      try {
        const value = String(reference || '');
        const sourceUrlPromise = value.startsWith('blob:') || value.startsWith('data:')
          ? Promise.resolve(value)
          : fetch(value).then(response => {
              if (!response.ok) throw Error('web_post_video_editor_source_' + response.status);
              return response.blob();
            }).then(blob => globalThis.URL.createObjectURL(blob));
        sourceUrlPromise.then(sourceUrl => {
          const video = globalThis.document.createElement('video');
          video.preload = 'metadata';
          video.muted = true;
          video.playsInline = true;
          video.onloadedmetadata = () => {
            const durationMs = Math.max(1, Number(video.duration || 0) * 1000);
            onSuccess(durationMs, Number(video.videoWidth || 0), Number(video.videoHeight || 0));
          };
          video.onerror = () => onFailure('web_post_video_editor_metadata_load_failed');
          video.src = sourceUrl;
        }).catch(error => onFailure(String(error?.message || error || 'web_post_video_editor_metadata_failed').slice(0, 160)));
      } catch (error) {
        onFailure(String(error?.message || error || 'web_post_video_editor_metadata_failed').slice(0, 160));
      }
    })()
    """,
)
