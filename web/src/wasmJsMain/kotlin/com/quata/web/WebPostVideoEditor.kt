@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.core.captions.core.CaptionDocument
import com.quata.core.captions.core.CaptionDocumentWireCodec
import com.quata.core.captions.templates.CaptionTemplateStyle
import com.quata.core.media.QuataVideoExportPolicy
import com.quata.feature.postcomposer.videoeditor.CaptionStyleOption
import com.quata.feature.postcomposer.videoeditor.DefaultPostVideoEditorExportProfile
import com.quata.feature.postcomposer.videoeditor.MaximumPostVideoEditorDurationMs
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorDialogContent
import com.quata.feature.postcomposer.videoeditor.PostVideoEditorUiState
import com.quata.feature.postcomposer.videoeditor.VideoCropMode
import com.quata.feature.postcomposer.videoeditor.cropRect
import com.quata.feature.postcomposer.videoeditor.postVideoEditorExportSpec
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateForSourceDuration
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropModeChange
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropPan
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCaptionsToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropToggle
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterCropZoomChange
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterReset
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimEnd
import com.quata.feature.postcomposer.videoeditor.postVideoEditorStateAfterTrimStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLVideoElement
import kotlinx.browser.document
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WebPostVideoEditor(
    sourceReference: String,
    isLandscapeLayout: Boolean,
    onDismiss: () -> Unit,
    onEdited: (String) -> Unit,
) {
    var state by remember(sourceReference) { mutableStateOf(PostVideoEditorUiState()) }
    var durationMs by remember(sourceReference) { mutableStateOf(MaximumPostVideoEditorDurationMs) }
    var videoAspectRatio by remember(sourceReference) { mutableStateOf(9f / 16f) }
    var metadataLoaded by remember(sourceReference) { mutableStateOf(false) }
    var timelineFrames by remember(sourceReference) { mutableStateOf<List<String>>(emptyList()) }
    var exportProfile by remember(sourceReference) { mutableStateOf(DefaultPostVideoEditorExportProfile) }
    var exportJob by remember(sourceReference) { mutableStateOf<Job?>(null) }
    var activeExportToken by remember(sourceReference) { mutableStateOf<Any?>(null) }
    val scope = rememberCoroutineScope()
    fun cancelExport() {
        activeExportToken = null
        exportJob?.cancel()
        exportJob = null
        state = state.copy(isExporting = false, exportProgress = 0f)
        webPostVideoEditorCancelActiveExport()
        webPostVideoEditorRecordExportFailure("web_post_video_editor_export_cancelled")
    }
    LaunchedEffect(sourceReference) {
        runCatching { webPostVideoEditorReadMetadata(sourceReference) }
            .onSuccess { metadata ->
                metadataLoaded = true
                durationMs = metadata.durationMs
                state = postVideoEditorStateForSourceDuration(state, metadata.durationMs)
                videoAspectRatio = metadata.aspectRatio
                exportProfile = QuataVideoExportPolicy.selectForSource(metadata.width, metadata.height)
                timelineFrames = webPostVideoEditorCreateTimelineFrames(sourceReference, metadata.durationMs, 6)
            }
            .onFailure {
                metadataLoaded = true
                state = state.copy(error = it.message ?: "web_post_video_editor_metadata_unavailable")
            }
    }
    fun export() {
        if (state.isExporting || exportJob != null) return
        if (!metadataLoaded) {
            state = state.copy(error = "web_post_video_editor_metadata_loading")
            return
        }
        val exportToken = Any()
        activeExportToken = exportToken
        state = state.copy(isExporting = true, exportProgress = 0.35f, error = null)
        webPostVideoEditorPrepareExportStart()
        webPostVideoEditorRecordExportStarted()
        val job = scope.launch {
            runCatching {
                val freshMetadata = webPostVideoEditorReadMetadata(sourceReference)
                if (activeExportToken !== exportToken) throw CancellationException("web_post_video_editor_export_cancelled")
                val exportDurationMs = freshMetadata.durationMs.also {
                    durationMs = it
                    state = postVideoEditorStateForSourceDuration(state, it)
                }
                val exportAspectRatio = freshMetadata.aspectRatio.also { videoAspectRatio = it }
                val selectedProfile = freshMetadata
                    .let { QuataVideoExportPolicy.selectForSource(it.width, it.height) }
                    .also { exportProfile = it }
                val shouldGenerateCaptions = state.captionsEnabled && state.selectedCaptionStyleId != null
                val captionDocument = if (shouldGenerateCaptions) {
                    webPostVideoEditorTranscribeCaptions(sourceReference)
                } else {
                    null
                }
                if (activeExportToken !== exportToken) throw CancellationException("web_post_video_editor_export_cancelled")
                val spec = postVideoEditorExportSpec(
                    state,
                    exportAspectRatio,
                    exportDurationMs,
                    captionDocument,
                    selectedProfile,
                )
                spec to webPostVideoEditorExportEdited(sourceReference, spec) { progress ->
                    state = state.copy(exportProgress = progress.coerceIn(0.35f, 0.95f))
                }
            }
                .onSuccess {
                    if (activeExportToken !== exportToken) return@onSuccess
                    val spec = it.first
                    val reference = it.second
                    activeExportToken = null
                    state = state.copy(isExporting = false, exportProgress = 1f)
                    webPostVideoEditorRecordExportSuccess(
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
                        captionDocumentWire = spec.captionDocument?.let(CaptionDocumentWireCodec::encodeDocument) ?: "",
                        outputWidth = spec.outputWidth,
                        outputHeight = spec.outputHeight,
                    )
                    onEdited(reference)
                }
                .onFailure {
                    if (activeExportToken !== exportToken) return@onFailure
                    activeExportToken = null
                    if (it is CancellationException) return@onFailure
                    val message = it.message ?: "web_post_video_editor_export_failed"
                    state = state.copy(isExporting = false, error = message)
                    webPostVideoEditorRecordExportFailure(message)
                }
        }
        exportJob = job
        job.invokeOnCompletion {
            if (activeExportToken === exportToken) activeExportToken = null
            if (exportJob === job) exportJob = null
        }
    }
    DisposableEffect(sourceReference, state, timelineFrames.size, onDismiss, onEdited) {
        val uninstall = installWebPostVideoEditorE2eBridge(
            mute = { state = state.copy(isMuted = !state.isMuted) },
            playPause = { state = state.copy(isPlaying = !state.isPlaying) },
            trimStart = { state = postVideoEditorStateAfterTrimStart(state, it, durationMs) },
            trimEnd = { state = postVideoEditorStateAfterTrimEnd(state, it, durationMs) },
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
            reset = { state = postVideoEditorStateAfterReset(state, durationMs) },
            export = { export() },
            dismiss = {
                if (state.isExporting) {
                    cancelExport()
                } else {
                    onDismiss()
                }
            },
            timelineFrameCount = { timelineFrames.size },
            timelineFrameEvidence = {
                timelineFrames.joinToString("|") { frame ->
                    "${frame.length}:${frame.take(32)}"
                }
            },
            isExporting = { state.isExporting },
            exportProgress = { state.exportProgress },
        )
        onDispose { uninstall() }
    }

    PostVideoEditorDialogContent(
        state = state,
        onMutedChange = { state = state.copy(isMuted = it) },
        onPlayPause = { state = state.copy(isPlaying = !state.isPlaying) },
        onTrimStartChange = { state = postVideoEditorStateAfterTrimStart(state, it, durationMs) },
        onTrimEndChange = { state = postVideoEditorStateAfterTrimEnd(state, it, durationMs) },
        onCropToggle = { state = postVideoEditorStateAfterCropToggle(state) },
        onCaptionsToggle = { state = postVideoEditorStateAfterCaptionsToggle(state) },
        onReset = { state = postVideoEditorStateAfterReset(state, durationMs) },
        onDismiss = {
            if (state.isExporting) cancelExport() else onDismiss()
        },
        onExport = ::export,
        onCancelExport = ::cancelExport,
        isLandscapeLayout = isLandscapeLayout,
        durationMs = durationMs,
        captionOptions = CaptionTemplateStyle.entries.map { CaptionStyleOption(it.name, it.name) },
        onCropModeChange = { state = postVideoEditorStateAfterCropModeChange(state, it, videoAspectRatio) },
        onCropZoomChange = { state = postVideoEditorStateAfterCropZoomChange(state, it, videoAspectRatio) },
        onCropPanChange = { dx, dy -> state = postVideoEditorStateAfterCropPan(state, dx, dy, videoAspectRatio) },
        onCaptionStyleChange = { state = state.copy(selectedCaptionStyleId = it, captionsEnabled = it != null) },
        onSeekChange = { state = state.copy(currentPositionFraction = it.coerceIn(0f, 1f)) },
        timelineFrameCount = timelineFrames.size.takeIf { it > 0 } ?: 6,
        timelineFrameContent = { index, frameModifier ->
            timelineFrames.getOrNull(index)?.let { frame ->
                BrowserCanvasImage(frame, null, ContentScale.Crop, frameModifier)
            } ?: androidx.compose.foundation.layout.Box(frameModifier)
        },
        preview = { modifier ->
            WebElementView(
                factory = {
                    webPostVideoEditorCreatePreviewElement()
                },
                update = {
                    webPostVideoEditorConfigurePreview(
                        root = it,
                        reference = sourceReference,
                        isMuted = state.isMuted,
                        isPlaying = state.isPlaying,
                        positionMs = (state.currentPositionFraction.coerceIn(0f, 1f) * durationMs).toLong(),
                        trimStartMs = (state.trimStartFraction.coerceIn(0f, 1f) * durationMs).toLong(),
                        trimEndMs = (state.trimEndFraction.coerceIn(0f, 1f) * durationMs).toLong(),
                        durationMs = durationMs,
                        videoAspectRatio = videoAspectRatio,
                        cropLeft = state.cropMode.cropRect(videoAspectRatio, state.cropZoom, androidx.compose.ui.geometry.Offset(state.cropCenterX, state.cropCenterY)).left,
                        cropTop = state.cropMode.cropRect(videoAspectRatio, state.cropZoom, androidx.compose.ui.geometry.Offset(state.cropCenterX, state.cropCenterY)).top,
                        cropRight = state.cropMode.cropRect(videoAspectRatio, state.cropZoom, androidx.compose.ui.geometry.Offset(state.cropCenterX, state.cropCenterY)).right,
                        cropBottom = state.cropMode.cropRect(videoAspectRatio, state.cropZoom, androidx.compose.ui.geometry.Offset(state.cropCenterX, state.cropCenterY)).bottom,
                        cropVisible = state.cropPanelOpen && state.cropMode != VideoCropMode.Original,
                        onPositionMsChange = { nextPositionMs ->
                            val bounded = nextPositionMs.toLong().coerceIn(0L, durationMs.coerceAtLeast(1L))
                            val nextFraction = bounded.toFloat() / durationMs.coerceAtLeast(1L).toFloat()
                            if (kotlin.math.abs(nextFraction - state.currentPositionFraction) > 0.002f) {
                                state = state.copy(currentPositionFraction = nextFraction)
                            }
                        },
                    )
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
    reset: () -> Unit,
    export: () -> Unit,
    dismiss: () -> Unit,
    timelineFrameCount: () -> Int,
    timelineFrameEvidence: () -> String,
    isExporting: () -> Boolean,
    exportProgress: () -> Float,
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
    reset,
    export,
    dismiss,
    timelineFrameCount,
    timelineFrameEvidence,
    isExporting,
    exportProgress,
)

@JsFun(
    """(mute, playPause, trimStart, trimEnd, crop, cropMode, cropZoom, cropPan, captions, captionStyle, reset, exportVideo, dismiss, timelineFrameCount, timelineFrameEvidence, isExporting, exportProgress) => {
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
        reset: () => reset(),
        export: () => exportVideo(),
        dismiss: () => dismiss(),
        timelineFrameCount: () => Number(timelineFrameCount()),
        timelineFrameEvidence: () => String(timelineFrameEvidence()),
        isExporting: () => Boolean(isExporting()),
        exportProgress: () => Number(exportProgress()),
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
    reset: () -> Unit,
    export: () -> Unit,
    dismiss: () -> Unit,
    timelineFrameCount: () -> Int,
    timelineFrameEvidence: () -> String,
    isExporting: () -> Boolean,
    exportProgress: () -> Float,
): () -> Unit

private fun webPostVideoEditorCreatePreviewElement(): HTMLElement = js(
    """(() => {
      const root = document.createElement('div');
      root.style.cssText = 'position:relative;width:100%;height:100%;overflow:hidden;background:#000;border-radius:16px;';
      const bg = document.createElement('video');
      const fgClip = document.createElement('div');
      const fg = document.createElement('video');
      for (const video of [bg, fg]) {
        video.controls = false;
        video.loop = true;
        video.preload = 'metadata';
        video.playsInline = true;
        video.style.position = 'absolute';
        video.style.top = '50%';
        video.style.left = '50%';
        video.style.width = '100%';
        video.style.height = '100%';
        video.style.transformOrigin = 'center center';
      }
      bg.dataset.layer = 'background';
      fgClip.dataset.layer = 'foreground-clip';
      fg.dataset.layer = 'foreground';
      bg.style.objectFit = 'cover';
      bg.style.filter = 'blur(22px)';
      bg.style.opacity = '0.78';
      fgClip.style.cssText = 'position:absolute;overflow:hidden;background:#000;';
      fg.style.objectFit = 'fill';
      const veil = document.createElement('div');
      veil.dataset.layer = 'veil';
      veil.style.cssText = 'position:absolute;inset:0;background:rgba(0,0,0,.18);pointer-events:none;';
      const overlay = document.createElement('div');
      overlay.dataset.layer = 'crop';
      overlay.style.cssText = 'position:absolute;border:3px solid #ff7a18;box-shadow:0 0 0 9999px rgba(0,0,0,.48);display:none;pointer-events:none;';
      fgClip.append(fg);
      root.append(bg, veil, fgClip, overlay);
      return root;
    })()"""
)

private fun webPostVideoEditorConfigurePreview(
    root: HTMLElement,
    reference: String,
    isMuted: Boolean,
    isPlaying: Boolean,
    positionMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    durationMs: Long,
    videoAspectRatio: Float,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    cropVisible: Boolean,
    onPositionMsChange: (Double) -> Unit,
): Unit = js(
    """(() => {
      const videos = Array.from(root.querySelectorAll('video'));
      const bg = root.querySelector('video[data-layer="background"]');
      const fgClip = root.querySelector('[data-layer="foreground-clip"]');
      const fg = root.querySelector('video[data-layer="foreground"]');
      const overlay = root.querySelector('[data-layer="crop"]');
      const referenceValue = String(reference || '');
      const positionSeconds = Math.max(0, Number(positionMs) || 0) / 1000;
      const trimStartSeconds = Math.max(0, Number(trimStartMs) || 0) / 1000;
      const trimEndSeconds = Math.max(trimStartSeconds + 0.05, Number(trimEndMs) || Number(durationMs) || 0) / 1000;
      const durationValueMs = Math.max(1, Number(durationMs) || 1);
      for (const video of videos) {
        if (video.src !== referenceValue) video.src = referenceValue;
        video.muted = video.dataset.layer === 'background' ? true : Boolean(isMuted);
        video.loop = false;
        const currentSeconds = Number(video.currentTime || 0);
        const outsideTrim = currentSeconds < trimStartSeconds - 0.05 || currentSeconds >= trimEndSeconds + 0.05;
        const shouldSeekToState = !isPlaying && Number.isFinite(positionSeconds) && Math.abs(currentSeconds - positionSeconds) > 0.12;
        const shouldSeekToTrimStart = isPlaying && outsideTrim;
        if (shouldSeekToState || shouldSeekToTrimStart) {
          try { video.currentTime = shouldSeekToTrimStart ? trimStartSeconds : positionSeconds; } catch (_) {}
        }
        if (isPlaying) {
          const result = video.play?.();
          if (result && typeof result.catch === 'function') result.catch(() => {});
        } else {
          video.pause?.();
        }
      }
      if (root.__quataPreviewTicker) {
        clearInterval(root.__quataPreviewTicker);
        root.__quataPreviewTicker = null;
      }
      if (isPlaying && fg) {
        root.__quataPreviewTicker = setInterval(() => {
          const current = Number(fg.currentTime || 0);
          if (current >= trimEndSeconds || current < trimStartSeconds - 0.05) {
            for (const video of videos) {
              try { video.currentTime = trimStartSeconds; } catch (_) {}
              video.muted = video.dataset.layer === 'background' ? true : Boolean(isMuted);
              const result = video.play?.();
              if (result && typeof result.catch === 'function') result.catch(() => {});
            }
            onPositionMsChange(trimStartSeconds * 1000);
          } else {
            onPositionMsChange(Math.max(0, Math.min(durationValueMs, current * 1000)));
          }
        }, 120);
      } else if (Number.isFinite(positionSeconds)) {
        onPositionMsChange(Math.max(0, Math.min(durationValueMs, positionSeconds * 1000)));
      }
      const left = Math.max(0, Math.min(1, Number(cropLeft) || 0));
      const top = Math.max(0, Math.min(1, Number(cropTop) || 0));
      const right = Math.max(left + 0.01, Math.min(1, Number(cropRight) || 1));
      const bottom = Math.max(top + 0.01, Math.min(1, Number(cropBottom) || 1));
      const cropWidth = right - left;
      const cropHeight = bottom - top;
      const rootWidth = Math.max(1, root.clientWidth || root.getBoundingClientRect?.().width || 1);
      const rootHeight = Math.max(1, root.clientHeight || root.getBoundingClientRect?.().height || 1);
      const outputAspect = 720 / 1280;
      let viewportWidth = rootHeight * outputAspect;
      let viewportHeight = rootHeight;
      if (viewportWidth > rootWidth) {
        viewportWidth = rootWidth;
        viewportHeight = rootWidth / outputAspect;
      }
      const viewportLeft = (rootWidth - viewportWidth) / 2;
      const viewportTop = (rootHeight - viewportHeight) / 2;
      const applied = cropVisible ? { left: 0, top: 0, right: 1, bottom: 1 } : { left, top, right, bottom };
      const appliedWidth = Math.max(0.01, applied.right - applied.left);
      const appliedHeight = Math.max(0.01, applied.bottom - applied.top);
      const safeVideoAspect = Math.max(0.1, Number(videoAspectRatio) || outputAspect);
      const foregroundAspect = Math.max(0.1, safeVideoAspect * appliedWidth / appliedHeight);
      let foregroundWidth = viewportHeight * foregroundAspect;
      let foregroundHeight = viewportHeight;
      if (foregroundWidth > viewportWidth) {
        foregroundWidth = viewportWidth;
        foregroundHeight = viewportWidth / foregroundAspect;
      }
      const foregroundLeft = viewportLeft + (viewportWidth - foregroundWidth) / 2;
      const foregroundTop = viewportTop + (viewportHeight - foregroundHeight) / 2;
      if (bg) {
        bg.style.left = viewportLeft + 'px';
        bg.style.top = viewportTop + 'px';
        bg.style.width = viewportWidth + 'px';
        bg.style.height = viewportHeight + 'px';
        bg.style.transform = 'none';
      }
      if (fgClip) {
        fgClip.style.left = foregroundLeft + 'px';
        fgClip.style.top = foregroundTop + 'px';
        fgClip.style.width = foregroundWidth + 'px';
        fgClip.style.height = foregroundHeight + 'px';
      }
      if (fg) {
        fg.style.left = (-applied.left / appliedWidth * foregroundWidth) + 'px';
        fg.style.top = (-applied.top / appliedHeight * foregroundHeight) + 'px';
        fg.style.width = (foregroundWidth / appliedWidth) + 'px';
        fg.style.height = (foregroundHeight / appliedHeight) + 'px';
        fg.style.transform = 'none';
      }
      if (overlay) {
        overlay.style.display = cropVisible ? 'block' : 'none';
        overlay.style.left = (foregroundLeft + left * foregroundWidth) + 'px';
        overlay.style.top = (foregroundTop + top * foregroundHeight) + 'px';
        overlay.style.width = (cropWidth * foregroundWidth) + 'px';
        overlay.style.height = (cropHeight * foregroundHeight) + 'px';
      }
    })()"""
)

internal suspend fun webPostVideoEditorExportEdited(
    reference: String,
    spec: com.quata.feature.postcomposer.videoeditor.PostVideoEditorExportSpec,
    onProgress: (Float) -> Unit = {},
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
        captionDocumentWire = spec.captionDocument?.let(CaptionDocumentWireCodec::encodeDocument) ?: "",
        outputWidth = spec.outputWidth,
        outputHeight = spec.outputHeight,
        outputMaxFrameRate = spec.outputMaxFrameRate,
        outputTargetBitrate = spec.outputTargetBitrate,
        onProgress = onProgress,
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
    captionDocumentWire: String,
    outputWidth: Int,
    outputHeight: Int,
): Unit = js(
    """(() => {
        const native = globalThis.__quataPostVideoEditorExportNative || {};
        const documentWire = String(captionDocumentWire || '');
        const captionSegments = parseCaptionDocument(documentWire);
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
            outputMaxFrameRate: Number(native.outputMaxFrameRate || 0),
            outputTargetBitrate: Number(native.outputTargetBitrate || 0),
            actualSourceDurationMs: Number(native.actualSourceDurationMs || 0),
            effectiveTrimStartMs: Number(native.effectiveTrimStartMs || 0),
            effectiveTrimEndMs: Number(native.effectiveTrimEndMs || 0),
            effectiveDurationMs: Number(native.effectiveDurationMs || 0),
            wallDurationMs: Number(native.wallDurationMs || 0),
            elapsedAtStopMs: Number(native.elapsedAtStopMs || 0),
            stopPaddingMs: Number(native.stopPaddingMs || 0),
            minimumCaptureFrames: Number(native.minimumCaptureFrames || 0),
            drawnFrameCount: Number(native.drawnFrameCount || 0),
            physicalBackgroundBlur: Boolean(native.physicalBackgroundBlur),
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
            captionDocumentWire: documentWire,
            captionSegments,
            captionText: captionSegments.map(segment => segment.text).join(' ').trim(),
          },
          operations: {
            trim: Number(trimStartMs) > 0 || Number(trimEndMs) < Number(sourceDurationMs),
            mute: Boolean(removeAudio),
            crop: cropChanged,
            captions: String(captionStyle || '').length > 0 && captionSegments.length > 0,
          },
        };
        function parseCaptionDocument(value) {
          return String(value || '').split(/\n\s*\n/g).map(chunk => {
            const words = chunk.split(/\n/g).map(line => {
              const parts = line.split('\t');
              const text = String(parts[0] || '').trim();
              const startMs = Math.max(0, Number(parts[1]) || 0);
              const endMs = Math.max(startMs + 1, Number(parts[2]) || startMs + 1);
              return text ? { text, startMs, endMs, confidence: Math.max(0, Math.min(1, Number(parts[3]) || 1)) } : null;
            }).filter(Boolean);
            if (!words.length) return null;
            return {
              words,
              startMs: Math.min(...words.map(word => word.startMs)),
              endMs: Math.max(...words.map(word => word.endMs)),
              text: words.map(word => word.text).join(' '),
            };
          }).filter(Boolean);
        }
      })()""",
)

private fun webPostVideoEditorRecordExportFailure(message: String): Unit = js(
    """(() => { globalThis.__quataPostVideoEditorExport = { status: 'failed', message: String(message).slice(0, 160) }; })()""",
)

private fun webPostVideoEditorPrepareExportStart(): Unit = js(
    """(() => {
      const active = globalThis.__quataPostVideoEditorActiveExport;
      if (!active || active.finished) {
        globalThis.__quataPostVideoEditorCancelRequested = false;
      }
    })()""",
)

private fun webPostVideoEditorCancelActiveExport(): Unit = js(
    """(() => {
      const active = globalThis.__quataPostVideoEditorActiveExport;
      if (active && typeof active.cancel === 'function') active.cancel();
      else globalThis.__quataPostVideoEditorCancelRequested = true;
    })()""",
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
    captionDocumentWire: String,
    outputWidth: Int,
    outputHeight: Int,
    outputMaxFrameRate: Int,
    outputTargetBitrate: Int,
    onProgress: (Float) -> Unit,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      try {
        if (!globalThis.URL?.createObjectURL || typeof fetch !== 'function') {
          onFailure('web_post_video_editor_blob_unsupported'); return;
        }
        if (globalThis.__quataPostVideoEditorCancelRequested) {
          onFailure('web_post_video_editor_export_cancelled'); return;
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
          let finished = false;
          let timer = null;
          let recorder = null;
          let stream = null;
          const failOnce = reason => {
            if (finished) return;
            finished = true;
            if (timer) {
              try { globalThis.cancelAnimationFrame?.(timer); } catch (_) {}
              try { clearInterval(timer); } catch (_) {}
              try { clearTimeout(timer); } catch (_) {}
            }
            try { video.pause?.(); } catch (_) {}
            try { stream?.getTracks?.().forEach(track => track.stop?.()); } catch (_) {}
            if (globalThis.__quataPostVideoEditorActiveExport === activeExport) {
              delete globalThis.__quataPostVideoEditorActiveExport;
            }
            reject(Error(reason));
          };
          const activeExport = {
            cancel: () => {
              globalThis.__quataPostVideoEditorCancelRequested = true;
              try {
                if (recorder && recorder.state !== 'inactive') recorder.stop?.();
              } catch (_) {}
              failOnce('web_post_video_editor_export_cancelled');
            }
          };
          globalThis.__quataPostVideoEditorActiveExport = activeExport;
          if (globalThis.__quataPostVideoEditorCancelRequested) {
            failOnce('web_post_video_editor_export_cancelled');
            return;
          }
          const canvas = globalThis.document.createElement('canvas');
          canvas.width = Math.max(2, Number(outputWidth) || 720);
          canvas.height = Math.max(2, Number(outputHeight) || 1280);
          const context = canvas.getContext('2d');
          if (!context) throw Error('web_post_video_editor_canvas_context_unavailable');
          const fps = Math.max(1, Math.min(60, Number(outputMaxFrameRate) || 30));
          const captureTickRate = fps;
          stream = canvas.captureStream?.(0) || canvas.captureStream?.(fps);
          if (!stream || typeof globalThis.MediaRecorder !== 'function') {
            throw Error('web_post_video_editor_media_recorder_unavailable');
          }
          const canvasTrack = stream.getVideoTracks?.()[0] || null;
          if (!removeAudio && typeof video.captureStream === 'function') {
            try {
              const mediaStream = video.captureStream();
              mediaStream?.getAudioTracks?.().forEach(track => stream.addTrack(track));
            } catch (_) {}
          }
          const chunks = [];
          let drawnFrameCount = 0;
          let exportStartedAt = 0;
          let elapsedAtStopMs = 0;
          let sourceFrozenAtTrimEnd = false;
          let recorderStarted = false;
          let recorderStopRequested = false;
          const mimeType = globalThis.MediaRecorder.isTypeSupported?.('video/webm;codecs=vp8')
            ? 'video/webm;codecs=vp8'
            : 'video/webm';
          recorder = new globalThis.MediaRecorder(stream, {
            mimeType,
            videoBitsPerSecond: Math.max(200000, Number(outputTargetBitrate) || 1200000)
          });
          recorder.ondataavailable = event => { if (event.data && event.data.size) chunks.push(event.data); };
          recorder.onerror = event => failOnce(event?.error?.message || 'web_post_video_editor_recorder_failed');
          function stopRecorderOnce() {
            if (recorderStopRequested) return;
            recorderStopRequested = true;
            if (recorder && recorder.state !== 'inactive') recorder.stop();
          }
          recorder.onstop = () => {
            if (finished || globalThis.__quataPostVideoEditorCancelRequested) return;
            const blob = new Blob(chunks, { type: mimeType });
            if (!blob.size) failOnce('web_post_video_editor_empty_export');
            else {
              finished = true;
              if (globalThis.__quataPostVideoEditorActiveExport === activeExport) {
                delete globalThis.__quataPostVideoEditorActiveExport;
              }
              globalThis.__quataPostVideoEditorExportNative = {
                mimeType,
                size: blob.size,
                outputWidth: canvas.width,
                outputHeight: canvas.height,
                outputMaxFrameRate: fps,
                outputTargetBitrate: Math.max(200000, Number(outputTargetBitrate) || 1200000),
                actualSourceDurationMs: actualDurationMs,
                effectiveTrimStartMs: startMs,
                effectiveTrimEndMs: endMs,
                effectiveDurationMs: durationMs,
                wallDurationMs: exportStartedAt > 0 ? Math.max(0, performance.now() - exportStartedAt) : 0,
                elapsedAtStopMs,
                stopPaddingMs,
                minimumCaptureFrames,
                drawnFrameCount,
                physicalBackgroundBlur: true,
              };
              const reference = globalThis.URL.createObjectURL(blob);
              const extension = mimeType.includes('webm') ? 'webm' : 'mp4';
              globalThis.__quataPostVideoEditorBlobMetadata = globalThis.__quataPostVideoEditorBlobMetadata || new Map();
              globalThis.__quataPostVideoEditorBlobMetadata.set(reference, {
                mimeType,
                name: 'video.' + extension,
                size: blob.size
              });
              resolve(reference);
            }
          };
          const actualDurationMs = Math.max(500, (Number(video.duration) || 0) * 1000);
          const hintedDurationMs = Math.max(actualDurationMs, Number(sourceDurationMs) || actualDurationMs);
          const sourceScale = actualDurationMs > 0 && hintedDurationMs > actualDurationMs * 1.5
            ? actualDurationMs / hintedDurationMs
            : 1;
          const startMs = Math.min(
            Math.max(0, Number(trimStartMs) || 0),
            Math.max(0, hintedDurationMs - 500)
          );
          const requestedEndMs = Math.max(startMs + 500, Number(trimEndMs) || hintedDurationMs);
          const endMs = Math.min(hintedDurationMs, requestedEndMs);
          const durationMs = Math.max(500, endMs - startMs);
          const stopPaddingMs = Math.max(920, 1000 / fps * 28);
          const minimumCaptureFrames = Math.max(1, Math.ceil((durationMs / 1000) * Math.min(captureTickRate, 24)));
          const sourceStartMs = Math.min(startMs * sourceScale, Math.max(0, actualDurationMs - 500));
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
          const captionSegments = parseCaptionDocument(captionDocumentWire);
          if (caption && !captionSegments.length) throw Error('web_post_video_editor_caption_transcript_missing');
          const sourceWidth = Math.max(1, video.videoWidth || canvas.width);
          const sourceHeight = Math.max(1, video.videoHeight || canvas.height);
          function parseCaptionDocument(value) {
            return String(value || '').split(/\n\s*\n/g).map(chunk => {
              const words = chunk.split(/\n/g).map(line => {
                const parts = line.split('\t');
                const text = String(parts[0] || '').trim();
                const startMs = Math.max(0, Number(parts[1]) || 0);
                const endMs = Math.max(startMs + 1, Number(parts[2]) || startMs + 1);
                return text ? { text, startMs, endMs, confidence: Math.max(0, Math.min(1, Number(parts[3]) || 1)) } : null;
              }).filter(Boolean);
              if (!words.length) return null;
              return {
                words,
                startMs: Math.min(...words.map(word => word.startMs)),
                endMs: Math.max(...words.map(word => word.endMs)),
                text: words.map(word => word.text).join(' '),
              };
            }).filter(Boolean);
          }
          function segmentAt(timeMs) {
            return captionSegments.find(segment => timeMs >= segment.startMs && timeMs <= segment.endMs) || null;
          }
          function captionRenderSpec(style) {
            if (style === 'PopWord') return { textSizeRatio: 0.092, maxWidthRatio: 0.92, maxLines: 1, verticalPosition: 0.67, lineHeightMultiplier: 1.0, uppercase: true, background: '#ff8a1a', segmentBackground: null, active: '#000000', normal: '#ffffff', font: '900 ' };
            if (style === 'Hormozi') return { textSizeRatio: 0.066, maxWidthRatio: 0.88, maxLines: 2, verticalPosition: 0.70, lineHeightMultiplier: 1.14, uppercase: true, background: '#ffe500', segmentBackground: 'rgba(0,0,0,0.82)', active: '#000000', normal: '#ffffff', font: '900 ' };
            if (style === 'Typewriter') return { textSizeRatio: 0.060, maxWidthRatio: 0.82, maxLines: 2, verticalPosition: 0.76, lineHeightMultiplier: 1.16, uppercase: false, background: null, segmentBackground: 'rgba(38,41,50,0.80)', active: '#ffffff', normal: '#ffffff', font: '700 ' };
            return { textSizeRatio: 0.058, maxWidthRatio: 0.84, maxLines: 2, verticalPosition: 0.74, lineHeightMultiplier: 1.12, uppercase: true, background: null, segmentBackground: 'rgba(0,0,0,0.69)', active: '#ff7a18', normal: '#ffffff', font: '900 ' };
          }
          function drawCaptionSegment(segment, timeMs) {
            if (!segment) return;
            const spec = captionRenderSpec(caption);
            const words = segment.words;
            const displayWords = words.map(word => spec.uppercase ? String(word.text || '').toUpperCase() : String(word.text || ''));
            const fontSize = Math.round(canvas.width * spec.textSizeRatio);
            context.font = spec.font + fontSize + 'px ' + (caption === 'Typewriter' ? 'monospace' : 'sans-serif');
            context.textAlign = 'left';
            const gap = fontSize * 0.34;
            const widths = displayWords.map(word => context.measureText(word).width);
            const maxLineWidth = canvas.width * spec.maxWidthRatio;
            const lines = [];
            let current = [];
            let currentWidth = 0;
            for (let index = 0; index < words.length; index += 1) {
              const nextWidth = current.length === 0 ? widths[index] : currentWidth + gap + widths[index];
              if (current.length > 0 && nextWidth > maxLineWidth && lines.length < spec.maxLines - 1) {
                lines.push({ items: current, width: currentWidth });
                current = [];
                currentWidth = 0;
              }
              current.push(index);
              currentWidth = currentWidth === 0 ? widths[index] : currentWidth + gap + widths[index];
            }
            if (current.length) lines.push({ items: current, width: currentWidth });
            const visibleLines = lines.slice(0, spec.maxLines);
            const lineHeight = fontSize * spec.lineHeightMultiplier;
            const top = canvas.height * spec.verticalPosition - (lineHeight * visibleLines.length) / 2;
            for (let lineIndex = 0; lineIndex < visibleLines.length; lineIndex += 1) {
              const line = visibleLines[lineIndex];
              const boxWidth = Math.min(maxLineWidth, Math.max(canvas.width * 0.24, line.width + canvas.width * 0.08));
              const boxHeight = Math.max(lineHeight * 1.08, canvas.height * 0.066);
              const boxX = (canvas.width - boxWidth) / 2;
              const boxY = top + lineHeight * lineIndex - boxHeight * 0.14;
              if (spec.segmentBackground || caption === 'PopWord') {
                context.fillStyle = spec.segmentBackground || 'rgba(255,138,26,0.88)';
                context.fillRect(boxX, boxY, boxWidth, boxHeight);
              }
              let x = (canvas.width - line.width) / 2;
              const y = top + lineHeight * lineIndex + fontSize * 0.82;
              for (const index of line.items) {
                const word = words[index];
                const active = timeMs >= word.startMs && timeMs <= word.endMs;
                if (active && spec.background) {
                  context.fillStyle = spec.background;
                  context.fillRect(x - gap * 0.32, y - fontSize * 0.88, widths[index] + gap * 0.64, fontSize * 1.12);
                }
                context.fillStyle = active ? spec.active : spec.normal;
                context.fillText(displayWords[index], x, y, Math.max(1, maxLineWidth));
                x += widths[index] + gap;
              }
            }
          }
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
          function drawFrame(exportTimeMs) {
            context.fillStyle = '#000000';
            context.fillRect(0, 0, canvas.width, canvas.height);
            context.save();
            context.filter = 'blur(22px)';
            drawCropFill(backgroundCrop);
            context.restore();
            context.fillStyle = 'rgba(0,0,0,0.24)';
            context.fillRect(0, 0, canvas.width, canvas.height);
            drawCropFit(crop);
            if (caption) {
              drawCaptionSegment(segmentAt(exportTimeMs), exportTimeMs);
            }
            context.fillStyle = drawnFrameCount % 2 === 0 ? 'rgba(0,0,0,0.35)' : 'rgba(255,255,255,0.35)';
            context.fillRect(0, 0, 1, 1);
            canvasTrack?.requestFrame?.();
          }
          video.onseeked = () => {
            if (recorderStarted) return;
            recorderStarted = true;
            if (recorder.state !== 'inactive') return;
            try {
              recorder.start(250);
            } catch (error) {
              failOnce(error?.message || 'web_post_video_editor_recorder_start_failed');
              return;
            }
            video.play?.().catch(() => {});
            exportStartedAt = performance.now();
            const tick = () => {
              if (globalThis.__quataPostVideoEditorCancelRequested) {
                try { stopRecorderOnce(); } catch (_) {}
                failOnce('web_post_video_editor_export_cancelled');
                return;
              }
              const elapsedMs = Math.max(0, performance.now() - exportStartedAt);
              if (!sourceFrozenAtTrimEnd && elapsedMs >= durationMs) {
                sourceFrozenAtTrimEnd = true;
                try {
                  video.pause?.();
                  video.currentTime = Math.min((sourceStartMs + durationMs) / 1000, Math.max(0, Number(video.duration) || 0));
                } catch (_) {}
              }
              drawFrame(Math.min(durationMs, elapsedMs));
              drawnFrameCount += 1;
              onProgress(Math.min(0.95, 0.35 + (elapsedMs / Math.max(1, durationMs)) * 0.6));
              const reachedCaptureTail = elapsedMs >= durationMs + stopPaddingMs && drawnFrameCount >= minimumCaptureFrames;
              if (reachedCaptureTail) {
                elapsedAtStopMs = elapsedMs;
                try { globalThis.cancelAnimationFrame?.(timer); } catch (_) {}
                try { clearTimeout(timer); } catch (_) {}
                video.pause?.();
                stopRecorderOnce();
                return;
              }
              timer = setTimeout(tick, 1000 / captureTickRate);
            };
            timer = setTimeout(tick, 1000 / captureTickRate);
          };
          video.currentTime = sourceStartMs / 1000;
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
    val width: Int,
    val height: Int,
)

private suspend fun webPostVideoEditorReadMetadata(reference: String): WebPostVideoEditorMetadata =
    suspendCoroutine { continuation ->
        webPostVideoEditorReadMetadataJs(
            reference = reference,
            onSuccess = { durationMs, width, height ->
                val safeDuration = durationMs.toLong().coerceAtLeast(1L)
                val safeAspectRatio = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 9f / 16f
                continuation.resume(WebPostVideoEditorMetadata(safeDuration, safeAspectRatio, width, height))
            },
            onFailure = { continuation.resumeWithException(IllegalStateException(it)) },
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

private suspend fun webPostVideoEditorCreateTimelineFrames(
    reference: String,
    durationMs: Long,
    count: Int,
): List<String> = suspendCoroutine { continuation ->
    webPostVideoEditorCreateTimelineFramesJs(
        reference = reference,
        durationMs = durationMs.toDouble(),
        count = count,
        onSuccess = { value ->
            continuation.resume(
                value.split('\n')
                    .map(String::trim)
                    .filter(String::isNotBlank),
            )
        },
        onFailure = { continuation.resume(emptyList()) },
    )
}

private fun webPostVideoEditorCreateTimelineFramesJs(
    reference: String,
    durationMs: Double,
    count: Int,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      try {
        const value = String(reference || '');
        const sourceUrlPromise = value.startsWith('blob:') || value.startsWith('data:')
          ? Promise.resolve(value)
          : fetch(value).then(response => {
              if (!response.ok) throw Error('web_post_video_editor_timeline_source_' + response.status);
              return response.blob();
            }).then(blob => globalThis.URL.createObjectURL(blob));
        sourceUrlPromise.then(sourceUrl => new Promise((resolve, reject) => {
          const video = document.createElement('video');
          video.muted = true;
          video.playsInline = true;
          video.preload = 'auto';
          video.src = sourceUrl;
          video.onerror = () => reject(Error('web_post_video_editor_timeline_decode_failed'));
          video.onloadedmetadata = async () => {
            const frameCount = Math.max(1, Math.min(10, Number(count) || 6));
            const actualDurationSeconds = Math.max(0.5, Number(video.duration || 0));
            const hintedDurationSeconds = Math.max(0.5, Number(durationMs || 0) / 1000);
            const totalSeconds = Math.min(actualDurationSeconds, hintedDurationSeconds);
            const canvas = document.createElement('canvas');
            canvas.width = 160;
            canvas.height = 90;
            const context = canvas.getContext('2d');
            if (!context) throw Error('web_post_video_editor_timeline_canvas_missing');
            const frames = [];
            for (let index = 0; index < frameCount; index += 1) {
              const fraction = frameCount === 1 ? 0.5 : index / (frameCount - 1);
              const target = Math.max(0, Math.min(totalSeconds - 0.05, totalSeconds * fraction));
              await new Promise(done => {
                const finish = () => { video.onseeked = null; done(); };
                video.onseeked = finish;
                try { video.currentTime = target; } catch (_) { finish(); }
                setTimeout(finish, 900);
              });
              context.fillStyle = '#000';
              context.fillRect(0, 0, canvas.width, canvas.height);
              const sourceWidth = Math.max(1, video.videoWidth || canvas.width);
              const sourceHeight = Math.max(1, video.videoHeight || canvas.height);
              const scale = Math.max(canvas.width / sourceWidth, canvas.height / sourceHeight);
              const drawWidth = sourceWidth * scale;
              const drawHeight = sourceHeight * scale;
              context.drawImage(video, (canvas.width - drawWidth) / 2, (canvas.height - drawHeight) / 2, drawWidth, drawHeight);
              frames.push(canvas.toDataURL('image/jpeg', 0.72));
            }
            resolve(frames.join('\n'));
          };
        })).then(onSuccess).catch(error => onFailure(String(error?.message || error || 'web_post_video_editor_timeline_failed')));
      } catch (error) {
        onFailure(String(error?.message || error || 'web_post_video_editor_timeline_failed'));
      }
    })()
    """,
)

private suspend fun webPostVideoEditorTranscribeCaptions(reference: String): CaptionDocument =
    suspendCoroutine { continuation ->
        webPostVideoEditorTranscribeCaptionsJs(
            reference = reference,
            onSuccess = { wordsWire ->
                val words = CaptionDocumentWireCodec.decodeWords(wordsWire)
                val document = CaptionDocument.fromWords(words)
                if (document.isEmpty) {
                    continuation.resumeWith(Result.failure(IllegalStateException("web_post_video_editor_caption_transcript_missing")))
                } else {
                    continuation.resume(document)
                }
            },
            onFailure = { reason -> continuation.resumeWith(Result.failure(IllegalStateException(reason))) },
        )
    }

private fun webPostVideoEditorTranscribeCaptionsJs(
    reference: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      const fail = reason => onFailure(String(reason || 'web_post_video_editor_caption_transcript_missing').slice(0, 180));
      const preferredLanguage = () => {
        const language = String(globalThis.navigator?.language || 'es').slice(0, 2).toLowerCase();
        return ['en', 'es', 'fr'].includes(language) ? language : 'en';
      };
      try {
        const sourcePromise = String(reference || '').startsWith('blob:') || String(reference || '').startsWith('data:')
          ? fetch(reference).then(response => response.arrayBuffer())
          : fetch(String(reference || '')).then(response => {
              if (!response.ok) throw Error('web_post_video_editor_caption_source_' + response.status);
              return response.arrayBuffer();
            });
        sourcePromise.then(async arrayBuffer => {
          const AudioContextCtor = globalThis.AudioContext || globalThis.webkitAudioContext;
          if (!AudioContextCtor) throw Error('web_post_video_editor_caption_audio_context_missing');
          const audioContext = new AudioContextCtor({ sampleRate: 16000 });
          const decoded = await audioContext.decodeAudioData(arrayBuffer.slice(0));
          const Vosk = await import('vosk-browser');
          const language = preferredLanguage();
          globalThis.__quataWebPostVideoEditorVoskModels = globalThis.__quataWebPostVideoEditorVoskModels || {};
          const modelUrl = `vosk/vosk-model-${'$'}{language}.tar.gz`;
          const model = globalThis.__quataWebPostVideoEditorVoskModels[language]
            || await Vosk.createModel(modelUrl, -1);
          globalThis.__quataWebPostVideoEditorVoskModels[language] = model;
          const recognizer = new model.KaldiRecognizer(decoded.sampleRate || 16000);
          recognizer.setWords(true);
          const words = [];
          let finalRequested = false;
          let settled = false;
          const finish = () => {
            if (settled) return;
            settled = true;
            recognizer.remove();
            runCatchingCloseAudioContext(audioContext);
            const wire = words.map(word => {
              const text = String(word.word || word.text || '').replace(/[\t\r\n|]+/g, ' ').trim();
              const startMs = Math.max(0, Math.round(Number(word.start || 0) * 1000));
              const endMs = Math.max(startMs + 1, Math.round(Number(word.end || 0) * 1000));
              const confidence = Math.max(0, Math.min(1, Number(word.conf || word.confidence || 1)));
              return text ? [text, startMs, endMs, confidence].join('\t') : '';
            }).filter(Boolean).join('\n');
            if (wire) onSuccess(wire);
            else fail('web_post_video_editor_caption_transcript_missing');
          };
          recognizer.on('result', message => {
            const resultWords = Array.isArray(message?.result?.result) ? message.result.result : [];
            for (const word of resultWords) {
              if (word && word.word) words.push(word);
            }
            if (finalRequested) globalThis.setTimeout(finish, 120);
          });
          recognizer.acceptWaveform(decoded);
          finalRequested = true;
          recognizer.retrieveFinalResult();
          globalThis.setTimeout(finish, 30000);
        }).catch(error => fail(error?.message || error || 'web_post_video_editor_caption_transcript_failed'));
      } catch (error) {
        fail(error?.message || error || 'web_post_video_editor_caption_transcript_failed');
      }

      function runCatchingCloseAudioContext(audioContext) {
        try {
          if (audioContext && audioContext.close) audioContext.close();
        } catch (_) {
        }
      }
    })()
    """,
)
