@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.postcomposer.videoeditor

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import android.app.Activity
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.DebugViewProvider
import androidx.media3.common.FrameInfo
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OnInputFrameProcessedListener
import androidx.media3.common.Player
import androidx.media3.common.SurfaceInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.VideoFrameProcessor
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.TimestampIterator
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size as GlSize
import androidx.media3.effect.Brightness
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.Crop
import androidx.media3.effect.DefaultVideoFrameProcessor
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.Size as Media3Size
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.quata.R
import com.quata.core.captions.android.CaptionPreviewRenderer
import com.quata.core.captions.media3.CaptionBurnInTrack
import com.quata.core.captions.media3.CaptionMedia3BurnIn
import com.quata.core.captions.templates.CaptionTemplateStyle
import com.quata.core.captions.transcriber.VoskModelDeliveryManager
import com.quata.core.captions.transcriber.VoskModelLanguage
import com.quata.core.captions.transcriber.VoskModelNotInstalledException
import com.quata.core.captions.transcriber.VoskVideoTranscriber
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.media.VideoExportProfile
import com.quata.core.media.VideoExportSystemProfile
import com.quata.core.media.withQuataMediaMetadataRetriever
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

@Composable
fun QuataVideoEditorDialog(
    videoUri: Uri,
    onDismiss: () -> Unit,
    onExported: (Uri) -> Unit
) {
    val context = LocalContext.current
    val template = quataTheme()
    val editorSourceUri = rememberVideoEditorSourceUri(videoUri)
    if (editorSourceUri == null) {
        VideoEditorPreparingSurface()
        return
    }
    DisposableEffect(editorSourceUri, videoUri) {
        onDispose {
            if (editorSourceUri != videoUri) {
                context.deleteVideoEditorSourceTemp(editorSourceUri)
            }
        }
    }
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val view = LocalView.current
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val metadata = rememberVideoEditorMetadata(editorSourceUri)
    val exportProfile = remember { VideoExportSystemProfile.current() }
    val videoAspect = metadata.aspectRatio ?: (9f / 16f)

    var trimStartMs by remember(editorSourceUri) { mutableLongStateOf(0L) }
    var trimEndMs by remember(editorSourceUri) { mutableLongStateOf(0L) }
    var durationApplied by remember(editorSourceUri) { mutableStateOf(false) }
    var isMuted by remember(editorSourceUri) { mutableStateOf(false) }
    var isCropPanelOpen by remember(editorSourceUri) { mutableStateOf(false) }
    var cropMode by remember(editorSourceUri) { mutableStateOf(VideoCropMode.Original) }
    var cropZoom by remember(editorSourceUri) { mutableFloatStateOf(1f) }
    var cropCenter by remember(editorSourceUri) { mutableStateOf(Offset(0.5f, 0.5f)) }
    var captionStyle by remember(editorSourceUri) { mutableStateOf<CaptionTemplateStyle?>(null) }
    var isCaptionPanelOpen by remember(editorSourceUri) { mutableStateOf(false) }
    var isExporting by remember(editorSourceUri) { mutableStateOf(false) }
    var exportProgress by remember(editorSourceUri) { mutableFloatStateOf(0f) }
    var exportError by remember(editorSourceUri) { mutableStateOf<String?>(null) }
    var exportJob by remember(editorSourceUri) { mutableStateOf<Job?>(null) }
    var isCancelExportDialogOpen by remember(editorSourceUri) { mutableStateOf(false) }
    var pendingCaptionModelLanguage by remember(editorSourceUri) { mutableStateOf<VoskModelLanguage?>(null) }
    var isCaptionModelDownloading by remember(editorSourceUri) { mutableStateOf(false) }
    var captionModelDownloadProgress by remember(editorSourceUri) { mutableStateOf<Float?>(null) }
    var isPreviewPlayerEnabled by remember(editorSourceUri) { mutableStateOf(true) }
    val voskModelDeliveryManager = remember(appContext) { VoskModelDeliveryManager(appContext) }
    val captionPreviewFrame = remember(appContext, captionStyle) {
        captionStyle?.let { style ->
            CaptionPreviewRenderer.renderPreviewBitmap(appContext, style, CaptionPreviewWidth, CaptionPreviewHeight)
        }
    }

    val player = remember(editorSourceUri, isPreviewPlayerEnabled) {
        if (!isPreviewPlayerEnabled) return@remember null
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(editorSourceUri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    var isPlaying by remember(player) { mutableStateOf(false) }
    var currentPositionMs by remember(player) { mutableLongStateOf(0L) }
    var playerDurationMs by remember(player) { mutableLongStateOf(0L) }
    val durationMs = maxOf(metadata.durationMs, playerDurationMs)
    val frames = rememberTimelineFrames(editorSourceUri, durationMs, metadata.rotation)
    val posterFrame = rememberVideoPosterFrame(editorSourceUri, metadata.rotation) ?: frames.firstOrNull()
    val dynamicPreviewFrame = rememberVideoPreviewFrame(
        uri = editorSourceUri,
        positionMs = currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)),
        enabled = isPlaying || currentPositionMs > 50L,
        rotationDegrees = metadata.rotation
    )
    val previewFrame = dynamicPreviewFrame ?: posterFrame

    val cropRect = remember(cropMode, cropZoom, cropCenter, videoAspect) {
        cropMode.cropRect(videoAspect, cropZoom, cropCenter)
    }

    LaunchedEffect(durationMs) {
        if (!durationApplied && durationMs > 0L) {
            trimStartMs = 0L
            trimEndMs = durationMs.coerceAtMost(MaximumTrimDurationMs)
            durationApplied = true
        }
    }

    LaunchedEffect(isMuted, player) {
        player?.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(player, trimStartMs, trimEndMs) {
        val activePlayer = player ?: return@LaunchedEffect
        while (true) {
            val position = activePlayer.currentPosition.coerceAtLeast(0L)
            currentPositionMs = position
            if (trimEndMs > trimStartMs && position >= trimEndMs) {
                activePlayer.pause()
                activePlayer.seekTo(trimStartMs)
                currentPositionMs = trimStartMs
            }
            delay(120L)
        }
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
        fun updatePlayerDuration() {
            val knownDuration = player.duration
            if (knownDuration > 0L && knownDuration != C.TIME_UNSET) {
                playerDurationMs = knownDuration
            }
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayerDuration()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                updatePlayerDuration()
            }
        }
        player.addListener(listener)
        updatePlayerDuration()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
        }
    }

    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, view, isExporting) {
        if (!isExporting) {
            onDispose { }
        } else {
            val previousKeepScreenOn = view.keepScreenOn
            val window = activity?.window
            val hadWindowKeepScreenOn = window
                ?.attributes
                ?.flags
                ?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            view.keepScreenOn = true
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                view.keepScreenOn = previousKeepScreenOn
                if (!hadWindowKeepScreenOn) {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    fun playOrPause() {
        val activePlayer = player ?: return
        if (activePlayer.isPlaying) {
            activePlayer.pause()
        } else {
            val position = activePlayer.currentPosition
            if (position < trimStartMs || position >= trimEndMs) {
                activePlayer.seekTo(trimStartMs)
            }
            activePlayer.play()
        }
    }

    fun seekPreviewTo(targetMs: Long) {
        if (durationMs <= 0L) return
        val boundedTarget = targetMs.coerceIn(0L, durationMs)
        player?.pause()
        player?.seekTo(boundedTarget)
        currentPositionMs = boundedTarget
    }

    fun applyTrimStartDrag(targetMs: Long) {
        if (durationMs <= 0L) return
        val boundedStart = targetMs.coerceIn(0L, (trimEndMs - MinimumTrimMs).coerceAtLeast(0L))
        val nextEnd = if (trimEndMs - boundedStart > MaximumTrimDurationMs) {
            (boundedStart + MaximumTrimDurationMs).coerceAtMost(durationMs)
        } else {
            trimEndMs
        }
        trimStartMs = boundedStart
        trimEndMs = nextEnd
        seekPreviewTo(boundedStart)
    }

    fun applyTrimEndDrag(targetMs: Long) {
        if (durationMs <= 0L) return
        val boundedEnd = targetMs.coerceIn((trimStartMs + MinimumTrimMs).coerceAtMost(durationMs), durationMs)
        val nextStart = if (boundedEnd - trimStartMs > MaximumTrimDurationMs) {
            (boundedEnd - MaximumTrimDurationMs).coerceAtLeast(0L)
        } else {
            trimStartMs
        }
        trimStartMs = nextStart
        trimEndMs = boundedEnd
        seekPreviewTo((boundedEnd - 33L).coerceAtLeast(nextStart))
    }

    fun suspendPreviewForExport() {
        currentPositionMs = player?.currentPosition?.coerceAtLeast(0L) ?: currentPositionMs
        isPlaying = false
        runCatching {
            player?.pause()
            player?.stop()
            player?.clearMediaItems()
        }
        isPreviewPlayerEnabled = false
    }

    fun restorePreviewAfterExportInterruption() {
        isPlaying = false
        isPreviewPlayerEnabled = true
    }

    fun requestBack() {
        if (isExporting) {
            isCancelExportDialogOpen = true
        } else {
            onDismiss()
        }
    }

    fun cancelExport() {
        isCancelExportDialogOpen = false
        exportError = null
        exportProgress = 0f
        isExporting = false
        exportJob?.cancel()
        exportJob = null
    }

    fun export() {
        if (isExporting || durationMs <= 0L || trimEndMs <= trimStartMs) return
        suspendPreviewForExport()
        exportError = null
        isCropPanelOpen = false
        isCaptionPanelOpen = false
        isExporting = true
        exportProgress = 0f
        val selectedCaptionStyle = captionStyle
        val selectedCropRect = cropRect.takeUnless { it.isFullFrame }
        val backgroundCropRect = if (cropRect.isFullFrame && metadata.hasNineSixteenAspect()) {
            null
        } else {
            cropRect
                .centerCropToAspect(EditorOutputAspectRatio, videoAspect)
                .takeUnless { it.isFullFrame }
        }
        val targetOutputWidth = exportProfile.width
        val targetOutputHeight = exportProfile.height
        exportJob = scope.launch {
            try {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    delay(250L)
                }
                val captionTrack = selectedCaptionStyle?.let { style ->
                    exportProgress = 0.03f
                    val captionDocument = VoskVideoTranscriber(appContext)
                        .transcribe(editorSourceUri)
                        .trimTo(trimStartMs, trimEndMs)
                    CaptionBurnInTrack(
                        document = captionDocument,
                        style = style,
                        outputWidth = targetOutputWidth,
                        outputHeight = targetOutputHeight
                    )
                }
                val request = VideoEditorExportRequest(
                    sourceUri = editorSourceUri,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    sourceDurationMs = durationMs,
                    removeAudio = isMuted,
                    cropRect = selectedCropRect,
                    backgroundCropRect = backgroundCropRect,
                    sourceWidth = metadata.displayWidth,
                    sourceHeight = metadata.displayHeight,
                    sourceRotation = metadata.rotation,
                    sourceFrameRate = metadata.frameRate,
                    sourceBitrate = metadata.bitrate,
                    outputWidth = targetOutputWidth,
                    outputHeight = targetOutputHeight,
                    exportProfile = exportProfile,
                    captionTrack = captionTrack
                )
                val exportedUri = appContext.exportEditedVideo(request) { progress ->
                    exportProgress = if (selectedCaptionStyle != null) {
                        0.12f + progress * 0.88f
                    } else {
                        progress
                    }
                }
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    delay(1_200L)
                }
                isCancelExportDialogOpen = false
                isExporting = false
                exportJob = null
                onExported(exportedUri)
            } catch (_: CancellationException) {
                isCancelExportDialogOpen = false
                exportProgress = 0f
                isExporting = false
                exportJob = null
                restorePreviewAfterExportInterruption()
            } catch (missingModel: VoskModelNotInstalledException) {
                isCancelExportDialogOpen = false
                exportProgress = 0f
                isExporting = false
                exportJob = null
                pendingCaptionModelLanguage = missingModel.language
                restorePreviewAfterExportInterruption()
            } catch (throwable: Throwable) {
                isCancelExportDialogOpen = false
                isExporting = false
                exportJob = null
                restorePreviewAfterExportInterruption()
                Log.e(VideoEditorLogTag, "export failed", throwable)
                exportError = throwable.message ?: appContext.getString(R.string.video_editor_export_failed)
            }
        }
    }

    fun downloadPendingCaptionModel() {
        val language = pendingCaptionModelLanguage ?: return
        if (isCaptionModelDownloading) return
        isCaptionModelDownloading = true
        captionModelDownloadProgress = null
        exportError = null
        scope.launch {
            try {
                voskModelDeliveryManager.install(language) { progress ->
                    captionModelDownloadProgress = progress.fraction
                }
                pendingCaptionModelLanguage = null
                isCaptionModelDownloading = false
                captionModelDownloadProgress = null
                export()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                isCaptionModelDownloading = false
                captionModelDownloadProgress = null
                exportError = throwable.message ?: appContext.getString(R.string.caption_model_download_failed)
            }
        }
    }

    BackHandler(onBack = ::requestBack)

    val selectedDurationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
    val exportProgressFraction = exportProgress.coerceIn(0f, 1f)
    val exportElapsedMs = (selectedDurationMs * exportProgressFraction).roundToLong()
    val timelinePositionMs = if (isExporting) {
        (trimStartMs + exportElapsedMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
    } else {
        currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }
    val infoPositionMs = if (isExporting) {
        timelinePositionMs
    } else {
        currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = template.colors.background,
        contentColor = template.colors.textPrimary
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 0.dp)
        ) {
            VideoEditorTopBar(
                isMuted = isMuted,
                isCropPanelOpen = isCropPanelOpen,
                isCaptionPanelOpen = isCaptionPanelOpen,
                hasCaptions = captionStyle != null,
                isExporting = isExporting,
                showTitle = !isLandscapeLayout,
                onBack = ::requestBack,
                onToggleMute = { isMuted = !isMuted },
                onToggleCrop = {
                    isCaptionPanelOpen = false
                    isCropPanelOpen = !isCropPanelOpen
                },
                onToggleCaptions = {
                    isCropPanelOpen = false
                    isCaptionPanelOpen = !isCaptionPanelOpen
                },
                onExport = ::export
            )

            if (isLandscapeLayout) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VideoPreviewPane(
                        player = player,
                        aspectRatio = videoAspect,
                        previewFrame = previewFrame,
                        isPlaying = isPlaying,
                        cropRect = cropRect,
                        videoRotationDegrees = metadata.rotation,
                        captionPreviewFrame = captionPreviewFrame,
                        isCropVisible = isCropPanelOpen && cropMode != VideoCropMode.Original,
                        onCropDrag = { dx, dy ->
                            val nextCenter = Offset(cropCenter.x + dx, cropCenter.y + dy)
                            cropCenter = cropMode.clampCenter(videoAspect, cropZoom, nextCenter)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    Column(
                        modifier = Modifier
                            .width(312.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VideoTimeline(
                            frames = frames,
                            durationMs = durationMs,
                            trimStartMs = trimStartMs,
                            trimEndMs = trimEndMs,
                            currentPositionMs = timelinePositionMs,
                            isExporting = isExporting,
                            onTrimStartChange = ::applyTrimStartDrag,
                            onTrimEndChange = ::applyTrimEndDrag,
                            onSeek = { target ->
                                seekPreviewTo(target)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp)
                        )

                        if (isCropPanelOpen && !isExporting) {
                            CropControls(
                                mode = cropMode,
                                zoom = cropZoom,
                                onModeChange = { mode ->
                                    cropMode = mode
                                    cropZoom = 1f
                                    cropCenter = Offset(0.5f, 0.5f)
                                },
                                onZoomChange = { nextZoom ->
                                    cropZoom = nextZoom
                                    cropCenter = cropMode.clampCenter(videoAspect, nextZoom, cropCenter)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (isCaptionPanelOpen && !isExporting) {
                            CaptionControls(
                                selectedStyle = captionStyle,
                                onStyleChange = { captionStyle = it },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        VideoEditorInfoBar(
                            currentPositionMs = infoPositionMs,
                            selectedDurationMs = selectedDurationMs,
                            isPlaying = isPlaying,
                            isExporting = isExporting,
                            exportProgress = exportProgress,
                            error = exportError,
                            onPlayPause = ::playOrPause
                        )
                    }
                }
            } else {
                VideoPreviewPane(
                    player = player,
                    aspectRatio = videoAspect,
                    previewFrame = previewFrame,
                    isPlaying = isPlaying,
                    cropRect = cropRect,
                    videoRotationDegrees = metadata.rotation,
                    captionPreviewFrame = captionPreviewFrame,
                    isCropVisible = isCropPanelOpen && cropMode != VideoCropMode.Original,
                    onCropDrag = { dx, dy ->
                        val nextCenter = Offset(cropCenter.x + dx, cropCenter.y + dy)
                        cropCenter = cropMode.clampCenter(videoAspect, cropZoom, nextCenter)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )

                VideoTimeline(
                    frames = frames,
                    durationMs = durationMs,
                    trimStartMs = trimStartMs,
                    trimEndMs = trimEndMs,
                    currentPositionMs = timelinePositionMs,
                    isExporting = isExporting,
                    onTrimStartChange = ::applyTrimStartDrag,
                    onTrimEndChange = ::applyTrimEndDrag,
                    onSeek = { target ->
                        seekPreviewTo(target)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .padding(horizontal = 48.dp)
                )

                if (isCropPanelOpen && !isExporting) {
                    CropControls(
                        mode = cropMode,
                        zoom = cropZoom,
                        onModeChange = { mode ->
                            cropMode = mode
                            cropZoom = 1f
                            cropCenter = Offset(0.5f, 0.5f)
                        },
                        onZoomChange = { nextZoom ->
                            cropZoom = nextZoom
                            cropCenter = cropMode.clampCenter(videoAspect, nextZoom, cropCenter)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
                if (isCaptionPanelOpen && !isExporting) {
                    CaptionControls(
                        selectedStyle = captionStyle,
                        onStyleChange = { captionStyle = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }

                VideoEditorInfoBar(
                    currentPositionMs = infoPositionMs,
                    selectedDurationMs = selectedDurationMs,
                    isPlaying = isPlaying,
                    isExporting = isExporting,
                    exportProgress = exportProgress,
                    error = exportError,
                    onPlayPause = ::playOrPause
                )
            }
        }
    }

    if (isCancelExportDialogOpen) {
        AlertDialog(
            onDismissRequest = { isCancelExportDialogOpen = false },
            title = { Text(stringResource(R.string.video_editor_cancel_export_title)) },
            text = { Text(stringResource(R.string.video_editor_cancel_export_message)) },
            confirmButton = {
                TextButton(onClick = ::cancelExport) {
                    Text(stringResource(R.string.video_editor_cancel_export_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { isCancelExportDialogOpen = false }) {
                    Text(stringResource(R.string.video_editor_continue_export))
                }
            }
        )
    }

    pendingCaptionModelLanguage?.let { language ->
        AlertDialog(
            onDismissRequest = {
                if (!isCaptionModelDownloading) pendingCaptionModelLanguage = null
            },
            title = { Text(stringResource(R.string.caption_model_download_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(
                            R.string.caption_model_download_message,
                            stringResource(language.titleRes)
                        )
                    )
                    if (isCaptionModelDownloading) {
                        captionModelDownloadProgress?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isCaptionModelDownloading,
                    onClick = ::downloadPendingCaptionModel
                ) {
                    Text(stringResource(R.string.caption_model_download_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isCaptionModelDownloading,
                    onClick = { pendingCaptionModelLanguage = null }
                ) {
                    Text(stringResource(R.string.caption_model_download_cancel))
                }
            }
        )
    }
}

@Composable
private fun VideoEditorTopBar(
    isMuted: Boolean,
    isCropPanelOpen: Boolean,
    isCaptionPanelOpen: Boolean,
    hasCaptions: Boolean,
    isExporting: Boolean,
    showTitle: Boolean,
    onBack: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCrop: () -> Unit,
    onToggleCaptions: () -> Unit,
    onExport: () -> Unit
) {
    val template = quataTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(template.colors.surfaceRaised)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIconButton(onClick = onBack, enabled = true) {
            CompactIcon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.video_editor_back),
                tint = template.colors.textPrimary
            )
        }
        Spacer(Modifier.width(6.dp))
        if (showTitle) {
            Text(
                text = stringResource(R.string.video_editor_title),
                color = template.colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
        if (!isExporting) {
            VideoToolButton(
                label = stringResource(if (isMuted) R.string.video_editor_unmute else R.string.video_editor_mute),
                enabled = true,
                onClick = onToggleMute
            ) {
                CompactIcon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
            }
            VideoToolButton(
                label = stringResource(if (isCropPanelOpen) R.string.video_editor_crop_done else R.string.video_editor_crop),
                enabled = true,
                onClick = onToggleCrop
            ) {
                CompactIcon(if (isCropPanelOpen) Icons.Filled.Check else Icons.Filled.Crop, contentDescription = null)
            }
            VideoToolButton(
                label = stringResource(if (isCaptionPanelOpen) R.string.video_editor_captions_done else R.string.video_editor_captions),
                enabled = true,
                selected = hasCaptions,
                onClick = onToggleCaptions
            ) {
                CompactIcon(if (isCaptionPanelOpen) Icons.Filled.Check else Icons.Filled.Subtitles, contentDescription = null)
            }
            VideoToolButton(
                label = stringResource(R.string.video_editor_export),
                enabled = true,
                onClick = onExport,
            ) {
                CompactIcon(Icons.Filled.Save, contentDescription = null)
            }
        }
    }
}

@Composable
private fun VideoToolButton(
    label: String,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val template = quataTheme()
    val contentAlpha = if (enabled) 1f else 0.42f
    val iconColor = if (selected) template.colors.accentContent else template.colors.textPrimary.copy(alpha = contentAlpha)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 66.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) template.colors.accent else template.colors.surfaceAlt.copy(alpha = if (enabled) 1f else 0.54f))
                .border(1.dp, if (selected) template.colors.accent else template.colors.divider, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = template.colors.textSecondary.copy(alpha = contentAlpha), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun VideoPreviewPane(
    player: ExoPlayer?,
    aspectRatio: Float,
    previewFrame: Bitmap?,
    isPlaying: Boolean,
    cropRect: NormalizedCropRect,
    videoRotationDegrees: Int,
    captionPreviewFrame: Bitmap?,
    isCropVisible: Boolean,
    onCropDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val previewAspectRatio = EditorOutputAspectRatio
        val appliedCrop = cropRect.takeIf { !isCropVisible && !it.isFullFrame } ?: NormalizedCropRect.Full
        val backgroundCrop = remember(cropRect, aspectRatio) {
            cropRect.centerCropToAspect(EditorOutputAspectRatio, aspectRatio)
        }
        val foregroundAspectRatio = appliedCrop.displayAspectRatio(aspectRatio)
        val widthFromHeight = maxHeight * previewAspectRatio
        val previewWidth: Dp
        val previewHeight: Dp
        if (widthFromHeight <= maxWidth) {
            previewWidth = widthFromHeight
            previewHeight = maxHeight
        } else {
            previewWidth = maxWidth
            previewHeight = maxWidth / previewAspectRatio
        }
        val foregroundWidthFromHeight = previewHeight * foregroundAspectRatio
        val foregroundWidth: Dp
        val foregroundHeight: Dp
        if (foregroundWidthFromHeight <= previewWidth) {
            foregroundWidth = foregroundWidthFromHeight
            foregroundHeight = previewHeight
        } else {
            foregroundWidth = previewWidth
            foregroundHeight = previewWidth / foregroundAspectRatio
        }
        val backgroundFrame = remember(previewFrame, backgroundCrop) {
            previewFrame?.cropNormalized(backgroundCrop)
        }
        val foregroundFrame = remember(previewFrame, appliedCrop) {
            previewFrame?.cropNormalized(appliedCrop)
        }
        val useBitmapPlayback = remember(appliedCrop, videoRotationDegrees) {
            appliedCrop.requiresLegacyBitmapPlayback(videoRotationDegrees)
        }
        val playbackPlayer = player

        Box(
            modifier = Modifier
                .size(previewWidth, previewHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            if (backgroundFrame != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(22.dp)
                        .align(Alignment.Center)
                ) {
                    Image(
                        bitmap = backgroundFrame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
            )
            Box(
                modifier = Modifier
                    .size(foregroundWidth, foregroundHeight)
                    .align(Alignment.Center)
                    .clipToBounds()
            ) {
                if (playbackPlayer != null && isPlaying && !isCropVisible && !useBitmapPlayback) {
                    AndroidView(
                        factory = { androidContext ->
                            TextureView(androidContext).apply {
                                isOpaque = true
                                playbackPlayer.setVideoTextureView(this)
                                tag = playbackPlayer
                            }
                        },
                        update = { textureView ->
                            if (textureView.tag !== playbackPlayer) {
                                (textureView.tag as? ExoPlayer)?.clearVideoTextureView(textureView)
                                playbackPlayer.setVideoTextureView(textureView)
                                textureView.tag = playbackPlayer
                            }
                            textureView.applyVideoEditorPlaybackTransform(appliedCrop, videoRotationDegrees)
                        },
                        onRelease = { textureView ->
                            (textureView.tag as? ExoPlayer)?.clearVideoTextureView(textureView)
                            textureView.tag = null
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                } else if (foregroundFrame != null) {
                    Image(
                        bitmap = foregroundFrame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    )
                }
            }
            if (isCropVisible) {
                Box(
                    modifier = Modifier
                        .size(foregroundWidth, foregroundHeight)
                        .align(Alignment.Center)
                ) {
                    CropOverlay(
                        rect = cropRect,
                        onDrag = onCropDrag,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            captionPreviewFrame?.let { preview ->
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun CropOverlay(
    rect: NormalizedCropRect,
    onDrag: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnDrag by rememberUpdatedState(onDrag)
    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                currentOnDrag(dragAmount.x / size.width, dragAmount.y / size.height)
            }
        }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val left = rect.left * size.width
            val top = rect.top * size.height
            val right = rect.right * size.width
            val bottom = rect.bottom * size.height
            val overlay = Color.Black.copy(alpha = 0.48f)
            drawRect(overlay, topLeft = Offset.Zero, size = Size(size.width, top))
            drawRect(overlay, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
            drawRect(overlay, topLeft = Offset(0f, top), size = Size(left, bottom - top))
            drawRect(overlay, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))
            drawRect(
                color = QuataOrange,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 4.dp.toPx())
            )
        }
        Box(
            modifier = Modifier
                .offset(x = maxWidth * rect.left, y = maxHeight * rect.top)
                .size(maxWidth * rect.width, maxHeight * rect.height)
        ) {
            CropCorner(Modifier.align(Alignment.TopStart))
            CropCorner(Modifier.align(Alignment.TopEnd))
            CropCorner(Modifier.align(Alignment.BottomStart))
            CropCorner(Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun CropCorner(modifier: Modifier) {
    Box(
        modifier = modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(QuataOrange)
            .border(1.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
    )
}

@Composable
private fun VideoTimeline(
    frames: List<Bitmap>,
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    isExporting: Boolean,
    onTrimStartChange: (Long) -> Unit,
    onTrimEndChange: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val safeDuration = durationMs.coerceAtLeast(1L)
    val startFraction = (trimStartMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val endFraction = (trimEndMs.toFloat() / safeDuration).coerceIn(startFraction, 1f)
    val handleWidth = 30.dp
    val handleHitWidth = 64.dp
    val handleHitPadding = (handleHitWidth - handleWidth) / 2f
    val currentTrimStartMs by rememberUpdatedState(trimStartMs)
    val currentTrimEndMs by rememberUpdatedState(trimEndMs)
    val baseModifier = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(template.colors.surfaceAlt)
    val interactiveModifier = if (isExporting) {
        baseModifier
    } else {
        baseModifier.pointerInput(durationMs) {
            fun positionToMs(x: Float): Long {
                val width = size.width.toFloat().coerceAtLeast(1f)
                return (x.coerceIn(0f, width) / width * safeDuration).roundToLong()
            }

            detectTapGestures { offset ->
                onSeek(positionToMs(offset.x))
            }
        }
    }

    BoxWithConstraints(
        modifier = interactiveModifier
    ) {
        val timelineWidthPx = with(LocalDensity.current) { maxWidth.toPx().coerceAtLeast(1f) }
        val handleWidthPx = with(LocalDensity.current) { handleWidth.toPx() }
        fun playheadX(widthPx: Float): Float {
            val startX = startFraction * widthPx + handleWidthPx
            val endX = (endFraction * widthPx - handleWidthPx).coerceAtLeast(startX)
            val selectedDuration = (trimEndMs - trimStartMs).coerceAtLeast(1L)
            val selectedFraction = ((currentPositionMs - trimStartMs).toFloat() / selectedDuration)
                .coerceIn(0f, 1f)
            return startX + (endX - startX) * selectedFraction
        }

        fun Modifier.handleDrag(marker: TimelineMarker): Modifier =
            if (isExporting) {
                this
            } else {
                pointerInput(durationMs, timelineWidthPx, marker) {
                    var initialStartMs = 0L
                    var initialEndMs = 0L
                    var accumulatedDeltaX = 0f

                    fun updateTrimFromDelta() {
                        val deltaMs = (accumulatedDeltaX / timelineWidthPx * safeDuration).roundToLong()
                        when (marker) {
                            TimelineMarker.Start -> {
                                val target = initialStartMs + deltaMs
                                onTrimStartChange(target)
                            }
                            TimelineMarker.End -> {
                                val target = initialEndMs + deltaMs
                                onTrimEndChange(target)
                            }
                        }
                    }

                    detectDragGestures(
                        onDragStart = {
                            initialStartMs = currentTrimStartMs
                            initialEndMs = currentTrimEndMs
                            accumulatedDeltaX = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDeltaX += dragAmount.x
                            updateTrimFromDelta()
                        }
                    )
                }
            }

        Row(Modifier.fillMaxSize()) {
            if (frames.isEmpty()) {
                repeat(TimelineFrameCount) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .background(Color.Black.copy(alpha = 0.36f))
                    )
                }
            } else {
                frames.forEach { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                    )
                }
            }
        }

        if (!isExporting) {
            Canvas(Modifier.fillMaxSize()) {
                val startX = startFraction * size.width
                val endX = endFraction * size.width
                drawRect(Color.Black.copy(alpha = 0.48f), topLeft = Offset.Zero, size = Size(startX, size.height))
                drawRect(Color.Black.copy(alpha = 0.48f), topLeft = Offset(endX, 0f), size = Size(size.width - endX, size.height))
                drawRect(
                    color = template.colors.accent,
                    topLeft = Offset(startX, 0f),
                    size = Size(endX - startX, size.height),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .offset(x = with(LocalDensity.current) { playheadX(timelineWidthPx).toDp() } - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(template.colors.textPrimary.copy(alpha = 0.88f))
        )

        if (!isExporting) {
            TimelineHandle(
                modifier = Modifier
                    .offset(x = maxWidth * startFraction)
                    .width(handleWidth)
                    .fillMaxHeight(),
                alignStart = true
            )
            TimelineHandle(
                modifier = Modifier
                    .offset(x = maxWidth * endFraction - handleWidth)
                    .width(handleWidth)
                    .fillMaxHeight(),
                alignStart = false
            )
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * startFraction - handleHitPadding)
                    .width(handleHitWidth)
                    .fillMaxHeight()
                    .handleDrag(TimelineMarker.Start)
            )
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * endFraction - handleWidth - handleHitPadding)
                    .width(handleHitWidth)
                    .fillMaxHeight()
                    .handleDrag(TimelineMarker.End)
            )
        }
    }
}

@Composable
private fun TimelineHandle(
    modifier: Modifier,
    alignStart: Boolean
) {
    val template = quataTheme()
    Box(
        modifier = modifier.background(template.colors.accent),
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.38f))
        )
    }
}

@Composable
private fun CropControls(
    mode: VideoCropMode,
    zoom: Float,
    onModeChange: (VideoCropMode) -> Unit,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(template.colors.surface)
            .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoCropMode.entries.forEach { option ->
                val selected = option == mode
                val colors = if (selected) {
                    ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent)
                } else {
                    ButtonDefaults.outlinedButtonColors(contentColor = template.colors.textPrimary)
                }
                val shape = RoundedCornerShape(9.dp)
                if (selected) {
                    Button(
                        onClick = { onModeChange(option) },
                        colors = colors,
                        shape = shape,
                        contentPadding = CompactButtonContentPadding,
                        modifier = Modifier.height(36.dp)
                    ) {
                        CompactIcon(Icons.Filled.AspectRatio, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(option.labelRes), fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onModeChange(option) },
                        colors = colors,
                        shape = shape,
                        contentPadding = CompactButtonContentPadding,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(stringResource(option.labelRes), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        if (mode != VideoCropMode.Original) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.video_editor_zoom),
                    color = template.colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(56.dp)
                )
                Slider(
                    value = zoom,
                    onValueChange = onZoomChange,
                    valueRange = 1f..3f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CaptionControls(
    selectedStyle: CaptionTemplateStyle?,
    onStyleChange: (CaptionTemplateStyle?) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(template.colors.surface)
            .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Text(
            text = stringResource(R.string.video_editor_captions),
            color = template.colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CaptionStyleButton(
                label = stringResource(R.string.caption_template_none),
                selected = selectedStyle == null,
                onClick = { onStyleChange(null) }
            )
            CaptionTemplateStyle.entries.forEach { style ->
                CaptionStyleButton(
                    label = stringResource(style.labelRes),
                    selected = selectedStyle == style,
                    onClick = { onStyleChange(style) }
                )
            }
        }
    }
}

@Composable
private fun CaptionStyleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val template = quataTheme()
    val shape = RoundedCornerShape(9.dp)
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
            shape = shape,
            contentPadding = CompactButtonContentPadding,
            modifier = Modifier.height(36.dp)
        ) {
            CompactIcon(Icons.Filled.Subtitles, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(label, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = template.colors.textPrimary),
            shape = shape,
            contentPadding = CompactButtonContentPadding,
            modifier = Modifier.height(36.dp)
        ) {
            Text(label, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
    }
}

@Composable
private fun VideoEditorInfoBar(
    currentPositionMs: Long,
    selectedDurationMs: Long,
    isPlaying: Boolean,
    isExporting: Boolean,
    exportProgress: Float,
    error: String?,
    onPlayPause: () -> Unit
) {
    val template = quataTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(template.colors.surfaceRaised)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        if (isExporting) {
            LinearProgressIndicator(
                progress = { exportProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = template.colors.accent,
                trackColor = template.colors.divider
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.video_editor_exporting, (exportProgress * 100).toInt().coerceIn(0, 100)),
                color = template.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeReadout(
                text = stringResource(R.string.video_editor_current_time, currentPositionMs.formatVideoTime()),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            )
            if (isExporting) {
                Spacer(modifier = Modifier.size(44.dp))
            } else {
                Surface(
                    color = template.colors.accent,
                    contentColor = template.colors.accentContent,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    CompactIconButton(onClick = onPlayPause, modifier = Modifier.fillMaxSize()) {
                        CompactIcon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.video_editor_play_pause),
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
            TimeReadout(
                text = stringResource(R.string.video_editor_duration, selectedDurationMs.formatVideoTime()),
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TimeReadout(
    text: String,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val label = text.substringBefore(':', text).trim()
    val value = text.substringAfter(':', "").trim()
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = template.colors.textSecondary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value.ifBlank { text },
            color = template.colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun rememberVideoEditorMetadata(uri: Uri): VideoEditorMetadata {
    val context = LocalContext.current
    var metadata by remember(uri) { mutableStateOf(VideoEditorMetadata()) }
    LaunchedEffect(uri) {
        metadata = withContext(Dispatchers.IO) {
            val retrieverMetadata = runCatching {
                withVideoMetadataRetriever { retriever ->
                    retriever.setSource(context, uri)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                    val retrieverFrameRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                    } else {
                        null
                    }
                    VideoEditorMetadata(
                        durationMs = duration,
                        width = width,
                        height = height,
                        rotation = rotation,
                        bitrate = bitrate,
                        frameRate = retrieverFrameRate ?: context.readVideoFrameRate(uri)
                    )
                }
            }.onFailure { throwable ->
                Log.w(VideoEditorLogTag, "metadataRetriever failed uri=$uri", throwable)
            }.getOrDefault(VideoEditorMetadata())
            val extractorMetadata = context.readVideoEditorExtractorMetadata(uri)
            retrieverMetadata.withFallback(extractorMetadata)
        }
    }
    return metadata
}

private fun VideoEditorMetadata.withFallback(fallback: VideoEditorMetadata): VideoEditorMetadata =
    VideoEditorMetadata(
        durationMs = durationMs.takeIf { it > 0L } ?: fallback.durationMs,
        width = width.takeIf { it > 0 } ?: fallback.width,
        height = height.takeIf { it > 0 } ?: fallback.height,
        rotation = rotation.normalizedVideoRotation().takeIf { it != 0 } ?: fallback.rotation.normalizedVideoRotation(),
        bitrate = bitrate ?: fallback.bitrate,
        frameRate = frameRate ?: fallback.frameRate
    )

private fun Context.readVideoEditorExtractorMetadata(uri: Uri): VideoEditorMetadata {
    val extractor = MediaExtractor()
    return try {
        extractor.setVideoEditorSource(this, uri)
        for (trackIndex in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(trackIndex)
            val mimeType = format.mimeType() ?: continue
            if (!mimeType.startsWith("video/")) continue
            val frameRate = format.videoEditorIntegerOrNull(MediaFormat.KEY_FRAME_RATE)?.toFloat()
            val durationUs = format.videoEditorLongOrNull(MediaFormat.KEY_DURATION)?.takeIf { it > 0L }
            return VideoEditorMetadata(
                durationMs = durationUs?.let { it / 1000L } ?: 0L,
                width = format.videoEditorIntegerOrNull(MediaFormat.KEY_WIDTH) ?: 0,
                height = format.videoEditorIntegerOrNull(MediaFormat.KEY_HEIGHT) ?: 0,
                rotation = format.videoEditorIntegerOrNull(MediaFormat.KEY_ROTATION)?.normalizedVideoRotation() ?: 0,
                bitrate = format.videoEditorIntegerOrNull(MediaFormat.KEY_BIT_RATE)?.toLong(),
                frameRate = frameRate
            )
        }
        VideoEditorMetadata()
    } catch (_: Throwable) {
        VideoEditorMetadata()
    } finally {
        extractor.release()
    }
}

@Composable
private fun rememberTimelineFrames(uri: Uri, durationMs: Long, rotationDegrees: Int): List<Bitmap> {
    val context = LocalContext.current
    val displayRotation = rotationDegrees.normalizedVideoRotation()
    var frames by remember(uri, displayRotation) { mutableStateOf(emptyList<Bitmap>()) }
    LaunchedEffect(uri, durationMs, displayRotation) {
        frames = emptyList()
        if (durationMs <= 0L) return@LaunchedEffect
        delay(TimelineFrameLoadDelayMs)
        frames = withContext(Dispatchers.IO) {
            runCatching {
                withVideoMetadataRetriever { retriever ->
                    retriever.setSource(context, uri)
                    List(TimelineFrameCount) { index ->
                        val fraction = if (TimelineFrameCount == 1) 0f else index.toFloat() / (TimelineFrameCount - 1)
                        val timeUs = (durationMs * 1000L * fraction).roundToLong()
                        retriever.getScaledVideoFrameAtTime(
                            timeUs = timeUs,
                            option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            maxDimension = TimelineFrameMaxDimension
                        )
                    }.filterNotNull()
                }
            }.getOrDefault(emptyList())
        }
    }
    return frames
}

private inline fun <T> withVideoMetadataRetriever(block: (MediaMetadataRetriever) -> T): T {
    return withQuataMediaMetadataRetriever(block)
}

private fun MediaMetadataRetriever.setSource(context: Context, uri: Uri) {
    when (uri.scheme) {
        "content" -> {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length >= 0L) {
                    setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                } else {
                    setDataSource(descriptor.fileDescriptor)
                }
                return
            }
            setDataSource(context, uri)
        }
        "file" -> {
            val path = uri.path ?: return setDataSource(context, uri)
            FileInputStream(path).use { stream ->
                setDataSource(stream.fd)
            }
        }
        else -> setDataSource(uri.toString(), emptyMap())
    }
}

private fun MediaExtractor.setVideoEditorSource(context: Context, uri: Uri) {
    if (uri.scheme == "file") {
        uri.path?.let { path ->
            FileInputStream(path).use { stream ->
                setDataSource(stream.fd)
            }
            return
        }
    }
    setDataSource(context, uri, null)
}

private fun Context.readVideoFrameRate(uri: Uri): Float? {
    val extractor = MediaExtractor()
    return try {
        extractor.setVideoEditorSource(this, uri)
        for (trackIndex in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(trackIndex)
            val mimeType = format.mimeType() ?: continue
            if (!mimeType.startsWith("video/")) continue
            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                return runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrNull()
            }
        }
        null
    } finally {
        extractor.release()
    }
}

private fun MediaMetadataRetriever.getScaledVideoFrameAtTime(
    timeUs: Long,
    option: Int,
    maxDimension: Int
): Bitmap? {
    val targetSize = scaledVideoFrameSize(maxDimension)
    val rawWidth = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
    val rawHeight = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull()
        ?.normalizedVideoRotation()
        ?: 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetSize != null) {
        runCatching {
            getScaledFrameAtTime(timeUs, option, targetSize.first, targetSize.second)
        }.getOrNull()
            ?.orientVideoFrameIfNeeded(rawWidth, rawHeight, rotation)
            ?.let { return it }
    }
    return getFrameAtTime(timeUs, option)
        ?.scaleToMaxDimension(maxDimension)
        ?.orientVideoFrameIfNeeded(rawWidth, rawHeight, rotation)
}

private fun Bitmap.orientVideoFrameIfNeeded(
    rawWidth: Int,
    rawHeight: Int,
    rotationDegrees: Int
): Bitmap {
    val rotation = rotationDegrees.normalizedVideoRotation()
    if (rotation != 90 && rotation != 270) return this
    if (rawWidth <= 0 || rawHeight <= 0 || width <= 0 || height <= 0) return this
    val expectedPortrait = rawHeight < rawWidth
    val bitmapPortrait = height > width
    if (expectedPortrait == bitmapPortrait) return this
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated !== this) recycle()
    return rotated
}

private fun MediaMetadataRetriever.scaledVideoFrameSize(maxDimension: Int): Pair<Int, Int>? {
    val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
    val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
    if (width <= 0 || height <= 0) return null
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull()
        ?.normalizedVideoRotation()
        ?: 0
    val displayWidth = if (rotation == 90 || rotation == 270) height else width
    val displayHeight = if (rotation == 90 || rotation == 270) width else height
    val largestDimension = maxOf(displayWidth, displayHeight)
    if (largestDimension <= 0) return null
    val scale = maxDimension.toFloat() / largestDimension.toFloat()
    val targetDisplayWidth = (displayWidth * scale).roundToInt().coerceAtLeast(1)
    val targetDisplayHeight = (displayHeight * scale).roundToInt().coerceAtLeast(1)
    return if (rotation == 90 || rotation == 270) {
        targetDisplayHeight to targetDisplayWidth
    } else {
        targetDisplayWidth to targetDisplayHeight
    }
}

private fun Bitmap.scaleForTimeline(): Bitmap = scaleToMaxDimension(TimelineFrameMaxDimension)

private fun Bitmap.scaleToMaxDimension(maxDimension: Int): Bitmap {
    val largestDimension = maxOf(width, height)
    if (largestDimension <= maxDimension) return this
    val scale = maxDimension.toFloat() / largestDimension.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    if (scaled !== this) recycle()
    return scaled
}

private fun Bitmap.scaleForPreviewPoster(): Bitmap = scaleToMaxDimension(PreviewPosterMaxDimension)

@Composable
private fun rememberVideoEditorSourceUri(sourceUri: Uri): Uri? {
    return sourceUri
}

@Composable
private fun VideoEditorPreparingSurface() {
    val template = quataTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(template.colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = template.colors.accent)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Preparing video...",
                color = template.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private suspend fun Context.prepareVideoEditorSourceUri(sourceUri: Uri): Uri =
    runCatching {
        val metadata = withContext(Dispatchers.IO) { readVideoEditorSourceMetadata(sourceUri) }
        if (metadata.rotation.normalizedVideoRotation() == 0) return@runCatching sourceUri
        val displayWidth = metadata.displayWidth.takeIf { it > 0 } ?: return@runCatching sourceUri
        val displayHeight = metadata.displayHeight.takeIf { it > 0 } ?: return@runCatching sourceUri
        val outputFile = createVideoEditorSourceTempFile()
        try {
            normalizeVideoEditorSource(
                sourceUri = sourceUri,
                outputFile = outputFile,
                outputWidth = displayWidth,
                outputHeight = displayHeight
            )
            Uri.fromFile(outputFile)
        } catch (throwable: Throwable) {
            runCatching { outputFile.delete() }
            throw throwable
        }
    }.onFailure { throwable ->
        Log.w(VideoEditorLogTag, "Could not normalize video editor source source=$sourceUri", throwable)
    }.getOrDefault(sourceUri)

private fun Context.readVideoEditorSourceMetadata(uri: Uri): VideoEditorMetadata {
    val retrieverMetadata = runCatching {
        withVideoMetadataRetriever { retriever ->
            retriever.setSource(this, uri)
            VideoEditorMetadata(
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0,
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull(),
                frameRate = null
            )
        }
    }.getOrDefault(VideoEditorMetadata())
    return retrieverMetadata.withFallback(readVideoEditorExtractorMetadata(uri))
}

private suspend fun Context.normalizeVideoEditorSource(
    sourceUri: Uri,
    outputFile: File,
    outputWidth: Int,
    outputHeight: Int
) {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            lateinit var transformer: Transformer
            val mediaItem = MediaItem.fromUri(sourceUri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(
                            Brightness(VideoEditorForceGlBrightness),
                            Presentation.createForWidthAndHeight(
                                outputWidth,
                                outputHeight,
                                Presentation.LAYOUT_SCALE_TO_FIT
                            )
                        )
                    )
                )
                .build()
            val composition = Composition.Builder(
                listOf(EditedMediaItemSequence.Builder(editedMediaItem).build())
            ).build()
            transformer = Transformer.Builder(this@normalizeVideoEditorSource)
                .setPortraitEncodingEnabled(true)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                runCatching { transformer.cancel() }
                runCatching { outputFile.delete() }
            }
            transformer.start(composition, outputFile.absolutePath)
        }
    }
}

private fun Context.createVideoEditorSourceTempFile(): File =
    File(cacheDir, "quata-editor-source-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}.mp4")

private fun Context.deleteVideoEditorSourceTemp(uri: Uri) {
    if (uri.scheme != "file") return
    val file = uri.path?.let(::File) ?: return
    if (file.name.startsWith("quata-editor-source-")) {
        runCatching { file.delete() }
    }
}

private fun Bitmap.cropNormalized(rect: NormalizedCropRect): Bitmap {
    if (rect.isFullFrame) return this
    val leftPx = (rect.left * width).roundToInt().coerceIn(0, width - 1)
    val topPx = (rect.top * height).roundToInt().coerceIn(0, height - 1)
    val rightPx = (rect.right * width).roundToInt().coerceIn(leftPx + 1, width)
    val bottomPx = (rect.bottom * height).roundToInt().coerceIn(topPx + 1, height)
    return Bitmap.createBitmap(this, leftPx, topPx, rightPx - leftPx, bottomPx - topPx)
}

private suspend fun Context.exportEditedVideo(
    request: VideoEditorExportRequest,
    onProgress: (Float) -> Unit
): Uri {
    if (request.canUseOriginalVideoInstantly()) {
        Log.d(
            VideoEditorLogTag,
            "Using original video without Transformer size=${request.sourceWidth}x${request.sourceHeight} " +
                "rotation=${request.sourceRotation} api=${Build.VERSION.SDK_INT}"
        )
        onProgress(1f)
        return request.sourceUri
    }
    val outputFile = createVideoEditorExportFile()
    if (request.canUseDirectStreamCopy()) {
        runCatching {
            return directStreamCopyEditedVideo(request, outputFile, onProgress)
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            Log.w(VideoEditorLogTag, "directStreamCopy failed; falling back to Transformer", throwable)
            runCatching { outputFile.delete() }
        }
    }
    if (request.shouldDownsampleBeforeTransformer()) {
        val intermediateFile = createVideoEditorIntermediateFile()
        return try {
            exportDownsampledIntermediate(request, intermediateFile) { progress ->
                onProgress(progress * DownsampleExportProgressShare)
            }
            val (intermediateWidth, intermediateHeight) = request.downsampledIntermediateSize()
            val finalRequest = request.copy(
                sourceUri = Uri.fromFile(intermediateFile),
                trimStartMs = 0L,
                trimEndMs = request.trimDurationMs,
                sourceDurationMs = request.trimDurationMs,
                sourceWidth = intermediateWidth,
                sourceHeight = intermediateHeight,
                sourceRotation = 0,
                sourceFrameRate = request.exportProfile.maxFrameRate.toFloat(),
                sourceBitrate = request.exportProfile.intermediateBitrate.toLong()
            )
            exportEditedVideoWithTransformer(finalRequest, outputFile) { progress ->
                onProgress(DownsampleExportProgressShare + progress * (1f - DownsampleExportProgressShare))
            }
        } finally {
            runCatching { intermediateFile.delete() }
        }
    }
    return exportEditedVideoWithTransformer(request, outputFile, onProgress)
}

private fun Context.createVideoEditorExportFile(): File =
    File(cacheDir, "quata-edited-video-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}.mp4")

private fun Context.createVideoEditorIntermediateFile(): File =
    File(cacheDir, "quata-editor-intermediate-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}.mp4")

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private suspend fun Context.exportDownsampledIntermediate(
    request: VideoEditorExportRequest,
    outputFile: File,
    onProgress: (Float) -> Unit
): Uri {
    val (outputWidth, outputHeight) = request.downsampledIntermediateSize()
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            lateinit var transformer: Transformer
            lateinit var progressRunnable: Runnable
            lateinit var completionFallbackRunnable: Runnable
            var stableOutputSizeBytes = -1L
            var stableOutputSinceMs = 0L
            fun completeFromStableOutput(reason: String) {
                handler.removeCallbacks(progressRunnable)
                handler.removeCallbacks(completionFallbackRunnable)
                onProgress(1f)
                Log.w(
                    VideoEditorLogTag,
                    "Completing Transformer export from stable output fallback reason=$reason output=${outputFile.name} size=${outputFile.length()}"
                )
                if (continuation.isActive) {
                    continuation.resume(Uri.fromFile(outputFile))
                }
            }
            fun checkStableOutputCompletion(reason: String): Boolean {
                if (progressHolder.progress < TransformerCompletionFallbackProgress) {
                    stableOutputSizeBytes = -1L
                    stableOutputSinceMs = 0L
                    return false
                }
                val nowMs = System.currentTimeMillis()
                val outputSizeBytes = outputFile.length()
                if (outputSizeBytes <= 0L) {
                    stableOutputSizeBytes = -1L
                    stableOutputSinceMs = 0L
                    return false
                }
                if (outputSizeBytes == stableOutputSizeBytes) {
                    if (stableOutputSinceMs == 0L) stableOutputSinceMs = nowMs
                    if (nowMs - stableOutputSinceMs >= TransformerStableOutputCompletionMs) {
                        completeFromStableOutput(reason)
                        return true
                    }
                } else {
                    stableOutputSizeBytes = outputSizeBytes
                    stableOutputSinceMs = nowMs
                }
                return false
            }
            progressRunnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress / 100f)
                    }
                    if (checkStableOutputCompletion("progress")) return
                    handler.postDelayed(this, 250L)
                }
            }
            completionFallbackRunnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    if (checkStableOutputCompletion("watchdog")) return
                    handler.postDelayed(this, 500L)
                }
            }

            val mediaItem = request.toClippedMediaItem()
            val frameRateEffects = request.frameRateEffects()
            val intermediateItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(request.removeAudio)
                .applyOutputFrameRateIfNeeded(request)
                .setEffects(
                    Effects(
                        emptyList(),
                        frameRateEffects + listOf(
                            Presentation.createForWidthAndHeight(
                                outputWidth,
                                outputHeight,
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                            )
                        )
                    )
                )
                .build()
            val composition = Composition.Builder(
                listOf(EditedMediaItemSequence.Builder(intermediateItem).build())
            ).build()
            val encoderFactory = DefaultEncoderFactory.Builder(this@exportDownsampledIntermediate)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(request.exportProfile.intermediateBitrate)
                        .build()
                )
                .build()
            transformer = Transformer.Builder(this@exportDownsampledIntermediate)
                .setPortraitEncodingEnabled(true)
                .setEncoderFactory(encoderFactory)
                .configureVideoEditorFrameProcessing(request)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(progressRunnable)
                        handler.removeCallbacks(completionFallbackRunnable)
                        onProgress(1f)
                        Log.d(VideoEditorLogTag, "transformerExport completed output=${outputFile.name} result=$exportResult")
                        if (continuation.isActive) continuation.resume(Uri.fromFile(outputFile))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        handler.removeCallbacks(progressRunnable)
                        handler.removeCallbacks(completionFallbackRunnable)
                        runCatching { outputFile.delete() }
                        Log.e(
                            VideoEditorLogTag,
                            "downsampleIntermediate failed output=${outputFile.absolutePath} " +
                                "source=${request.sourceWidth}x${request.sourceHeight} " +
                                "target=${outputWidth}x$outputHeight result=$exportResult",
                            exportException
                        )
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                handler.removeCallbacks(progressRunnable)
                handler.removeCallbacks(completionFallbackRunnable)
                runCatching { transformer.cancel() }
                runCatching { outputFile.delete() }
            }
            transformer.start(composition, outputFile.absolutePath)
            handler.post(progressRunnable)
            handler.postDelayed(completionFallbackRunnable, 500L)
        }
    }
}

private suspend fun Context.exportEditedVideoWithTransformer(
    request: VideoEditorExportRequest,
    outputFile: File,
    onProgress: (Float) -> Unit
): Uri {
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            lateinit var transformer: Transformer
            lateinit var progressRunnable: Runnable
            lateinit var completionFallbackRunnable: Runnable
            var stableOutputSizeBytes = -1L
            var stableOutputSinceMs = 0L
            fun completeFromStableOutput(reason: String) {
                handler.removeCallbacks(progressRunnable)
                handler.removeCallbacks(completionFallbackRunnable)
                onProgress(1f)
                Log.w(
                    VideoEditorLogTag,
                    "Completing Transformer export from stable output fallback reason=$reason output=${outputFile.name} size=${outputFile.length()}"
                )
                if (continuation.isActive) {
                    continuation.resume(Uri.fromFile(outputFile))
                }
            }
            fun checkStableOutputCompletion(reason: String): Boolean {
                if (progressHolder.progress < TransformerCompletionFallbackProgress) {
                    stableOutputSizeBytes = -1L
                    stableOutputSinceMs = 0L
                    return false
                }
                val nowMs = System.currentTimeMillis()
                val outputSizeBytes = outputFile.length()
                if (outputSizeBytes < TransformerStableOutputMinBytes) {
                    stableOutputSizeBytes = -1L
                    stableOutputSinceMs = 0L
                    return false
                }
                if (outputSizeBytes == stableOutputSizeBytes) {
                    if (stableOutputSinceMs == 0L) stableOutputSinceMs = nowMs
                    if (nowMs - stableOutputSinceMs >= TransformerStableOutputCompletionMs) {
                        completeFromStableOutput(reason)
                        return true
                    }
                } else {
                    stableOutputSizeBytes = outputSizeBytes
                    stableOutputSinceMs = nowMs
                }
                return false
            }
            progressRunnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress / 100f)
                    }
                    if (checkStableOutputCompletion("progress")) return
                    handler.postDelayed(this, 250L)
                }
            }
            completionFallbackRunnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    if (checkStableOutputCompletion("watchdog")) return
                    handler.postDelayed(this, 500L)
                }
            }

            val mediaItem = request.toClippedMediaItem()
            val cropEffects = request.cropRect?.let { listOf<Effect>(it.toMedia3Crop()) }.orEmpty()
            val frameRateEffects = request.frameRateEffects()
            val needsBackground = request.needsBlurredBackground()
            val useLegacySingleInputBackground = request.canUseLegacySingleInputBackground()
            val compositionEffects = CaptionMedia3BurnIn.effectsFor(request.captionTrack)
            val composition = if (useLegacySingleInputBackground) {
                val foregroundEffects = buildList {
                    addAll(frameRateEffects)
                    add(
                        LegacyBlurredFitEffect(
                            outputWidth = request.outputWidth,
                            outputHeight = request.outputHeight,
                            rotationDegrees = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                                request.sourceRotation
                            } else {
                                0
                            },
                            displayInputWidth = request.sourceWidth,
                            displayInputHeight = request.sourceHeight,
                            foregroundCropRect = request.cropRect ?: NormalizedCropRect.Full,
                            backgroundCropRect = request.backgroundCropRect ?: NormalizedCropRect.Full
                        )
                    )
                }
                val foregroundItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(request.removeAudio)
                    .applyOutputFrameRateIfNeeded(request)
                    .setEffects(Effects(emptyList(), foregroundEffects))
                    .build()
                Composition.Builder(
                    listOf(EditedMediaItemSequence.Builder(foregroundItem).build())
                ).build()
            } else if (needsBackground) {
                val backgroundCropEffects = request.backgroundCropRect
                    ?.let { listOf<Effect>(it.toMedia3Crop()) }
                    .orEmpty()
                val backgroundEffects = buildList {
                    addAll(frameRateEffects)
                    addAll(backgroundCropEffects)
                    add(
                        Presentation.createForWidthAndHeight(
                            request.outputWidth,
                            request.outputHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        )
                    )
                    add(GaussianBlur(EditorBackgroundBlurSigma))
                }
                val backgroundItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(true)
                    .applyOutputFrameRateIfNeeded(request)
                    .setEffects(Effects(emptyList(), backgroundEffects))
                    .build()
                val foregroundItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(request.removeAudio)
                    .applyOutputFrameRateIfNeeded(request)
                    .setEffects(Effects(emptyList(), frameRateEffects + cropEffects))
                    .build()
                Composition.Builder(
                    listOf(
                        EditedMediaItemSequence.Builder(foregroundItem).build(),
                        EditedMediaItemSequence.Builder(backgroundItem).build()
                    )
                )
                    .setVideoCompositorSettings(
                        NineSixteenVideoCompositorSettings(
                            foregroundScale = request.foregroundScale(),
                            outputWidth = request.outputWidth,
                            outputHeight = request.outputHeight
                        )
                    )
                    .setEffects(Effects(emptyList(), compositionEffects))
                    .build()
            } else {
                val foregroundEffects = buildList {
                    addAll(frameRateEffects)
                    addAll(cropEffects)
                    add(
                        Presentation.createForWidthAndHeight(
                            request.outputWidth,
                            request.outputHeight,
                            Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                        )
                    )
                }
                val foregroundItem = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(request.removeAudio)
                    .applyOutputFrameRateIfNeeded(request)
                    .setEffects(Effects(emptyList(), foregroundEffects))
                    .build()
                Composition.Builder(
                    listOf(EditedMediaItemSequence.Builder(foregroundItem).build())
                )
                    .setEffects(Effects(emptyList(), compositionEffects))
                    .build()
            }
            val encoderFactory = DefaultEncoderFactory.Builder(this@exportEditedVideoWithTransformer)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(request.exportProfile.targetBitrate)
                        .build()
                )
                .build()
            transformer = Transformer.Builder(this@exportEditedVideoWithTransformer)
                .setPortraitEncodingEnabled(true)
                .setEncoderFactory(encoderFactory)
                .configureVideoEditorFrameProcessing(request)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(progressRunnable)
                        handler.removeCallbacks(completionFallbackRunnable)
                        onProgress(1f)
                        Log.d(VideoEditorLogTag, "transformerExport completed output=${outputFile.name} result=$exportResult")
                        if (continuation.isActive) continuation.resume(Uri.fromFile(outputFile))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        handler.removeCallbacks(progressRunnable)
                        handler.removeCallbacks(completionFallbackRunnable)
                        runCatching { outputFile.delete() }
                        Log.e(
                            VideoEditorLogTag,
                            "transformerExport failed output=${outputFile.absolutePath} " +
                                "source=${request.sourceWidth}x${request.sourceHeight} " +
                                "target=${request.outputWidth}x${request.outputHeight} " +
                                "background=${request.needsBlurredBackground()} " +
                                "caption=${request.captionTrack != null} result=$exportResult",
                            exportException
                        )
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                handler.removeCallbacks(progressRunnable)
                handler.removeCallbacks(completionFallbackRunnable)
                runCatching { transformer.cancel() }
                runCatching { outputFile.delete() }
            }
            transformer.start(composition, outputFile.absolutePath)
            handler.post(progressRunnable)
            handler.postDelayed(completionFallbackRunnable, 500L)
        }
    }
}

private fun VideoEditorExportRequest.toClippedMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(sourceUri)
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(trimStartMs)
                .setEndPositionMs(trimEndMs)
                .build()
        )
        .build()

private suspend fun Context.directStreamCopyEditedVideo(
    request: VideoEditorExportRequest,
    outputFile: File,
    onProgress: (Float) -> Unit
): Uri = withContext(Dispatchers.IO) {
    var muxer: MediaMuxer? = null
    var muxerStarted = false
    var completed = false
    val extractor = MediaExtractor()
    try {
        currentCoroutineContext().ensureActive()
        extractor.setDataSource(this@directStreamCopyEditedVideo, request.sourceUri, null)
        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        if (request.sourceRotation != 0) {
            muxer.setOrientationHint(request.sourceRotation)
        }

        val muxedTracks = linkedMapOf<Int, Int>()
        var hasVideoTrack = false
        var maxInputSize = DirectStreamCopyDefaultBufferSize
        for (trackIndex in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(trackIndex)
            val mimeType = format.mimeType() ?: continue
            val isVideo = mimeType.startsWith("video/")
            val isAudio = mimeType.startsWith("audio/")
            if (!isVideo && !isAudio) continue
            if (isAudio && request.removeAudio) continue
            val muxedTrackIndex = muxer.addTrack(format)
            muxedTracks[trackIndex] = muxedTrackIndex
            hasVideoTrack = hasVideoTrack || isVideo
            maxInputSize = maxOf(maxInputSize, format.maxInputSizeOrNull() ?: DirectStreamCopyDefaultBufferSize)
        }
        check(hasVideoTrack) { "No video track available for direct stream copy" }
        check(muxedTracks.isNotEmpty()) { "No tracks available for direct stream copy" }

        muxedTracks.keys.forEach(extractor::selectTrack)
        val startUs = (request.trimStartMs * 1000L).coerceAtLeast(0L)
        val endUs = (request.trimEndMs * 1000L).coerceAtLeast(startUs)
        if (startUs > 0L) {
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }
        muxer.start()
        muxerStarted = true
        withContext(Dispatchers.Main) { onProgress(0.02f) }

        val buffer = ByteBuffer.allocateDirect(maxInputSize.coerceAtLeast(DirectStreamCopyDefaultBufferSize))
        val bufferInfo = MediaCodec.BufferInfo()
        var firstPresentationTimeUs: Long? = null
        var lastProgressUpdateMs = 0L
        val selectedDurationUs = (endUs - startUs).coerceAtLeast(1L)

        while (true) {
            currentCoroutineContext().ensureActive()
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex < 0) break
            val muxedTrackIndex = muxedTracks[trackIndex]
            if (muxedTrackIndex == null) {
                extractor.advance()
                continue
            }
            val sampleTimeUs = extractor.sampleTime
            if (sampleTimeUs < 0L || sampleTimeUs > endUs) break
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            val firstTimeUs = firstPresentationTimeUs ?: sampleTimeUs.also { firstPresentationTimeUs = it }
            buffer.position(0)
            buffer.limit(sampleSize)
            bufferInfo.set(
                0,
                sampleSize,
                (sampleTimeUs - firstTimeUs).coerceAtLeast(0L),
                extractor.sampleFlags
            )
            muxer.writeSampleData(muxedTrackIndex, buffer, bufferInfo)

            val nowMs = System.currentTimeMillis()
            if (nowMs - lastProgressUpdateMs > DirectStreamProgressIntervalMs) {
                lastProgressUpdateMs = nowMs
                val progress = ((sampleTimeUs - startUs).toFloat() / selectedDurationUs)
                    .coerceIn(0f, 1f)
                withContext(Dispatchers.Main) { onProgress(progress) }
            }
            extractor.advance()
        }

        muxer.stop()
        completed = true
        withContext(Dispatchers.Main) { onProgress(1f) }
        Uri.fromFile(outputFile)
    } catch (throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        throw throwable
    } finally {
        extractor.release()
        runCatching {
            if (!completed && muxerStarted) {
                muxer?.stop()
            }
        }
        runCatching { muxer?.release() }
        if (!completed) {
            runCatching { outputFile.delete() }
        }
    }
}

private fun MediaFormat.mimeType(): String? =
    if (containsKey(MediaFormat.KEY_MIME)) getString(MediaFormat.KEY_MIME) else null

private fun MediaFormat.maxInputSizeOrNull(): Int? =
    if (containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
        runCatching { getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrNull()
    } else {
        null
    }

private fun MediaFormat.videoEditorIntegerOrNull(key: String): Int? =
    if (containsKey(key)) {
        runCatching { getInteger(key) }.getOrNull()
    } else {
        null
    }

private fun MediaFormat.videoEditorLongOrNull(key: String): Long? =
    if (containsKey(key)) {
        runCatching { getLong(key) }.getOrNull()
    } else {
        null
    }

private fun VideoEditorExportRequest.canUseDirectStreamCopy(): Boolean =
    captionTrack == null &&
        cropRect == null &&
        (
            (
                backgroundCropRect == null &&
                    sourceRotation.normalizedVideoRotation() == 0 &&
                    hasNineSixteenSourceAspect() &&
                    isWithinDirectStreamSizeLimit() &&
                    isWithinDirectStreamBitrateLimit()
            ) ||
                canUseLegacyUntransformedStreamCopy()
        )

private fun VideoEditorExportRequest.canUseLegacyUntransformedStreamCopy(): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.O &&
        captionTrack == null &&
        cropRect == null &&
        backgroundCropRect == null &&
        isWithinLegacyDirectStreamSizeLimit()

private fun VideoEditorExportRequest.isWithinLegacyDirectStreamSizeLimit(): Boolean {
    if (sourceWidth <= 0 || sourceHeight <= 0) return false
    return minOf(sourceWidth, sourceHeight) <= LegacyDirectStreamShortSideLimit &&
        maxOf(sourceWidth, sourceHeight) <= LegacyDirectStreamLongSideLimit
}

private fun VideoEditorExportRequest.canUseOriginalVideoInstantly(): Boolean =
    canUseDirectStreamCopy() &&
        !removeAudio &&
        trimStartMs <= DirectOriginalTrimToleranceMs &&
        trimEndMs >= sourceDurationMs - DirectOriginalTrimToleranceMs

private fun VideoEditorExportRequest.hasNineSixteenSourceAspect(): Boolean {
    if (sourceWidth <= 0 || sourceHeight <= 0) return false
    val aspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    return abs(aspect - EditorOutputAspectRatio) <= DirectStreamAspectTolerance
}

private fun VideoEditorExportRequest.isWithinDirectStreamSizeLimit(): Boolean =
    sourceWidth in 1..outputWidth && sourceHeight in 1..outputHeight

private fun VideoEditorExportRequest.isWithinDirectStreamBitrateLimit(): Boolean {
    val bitrate = sourceBitrate ?: return false
    return bitrate <= exportProfile.targetBitrate + DirectStreamAudioBitrateAllowance
}

private fun VideoEditorExportRequest.needsBlurredBackground(): Boolean =
    backgroundCropRect != null

private fun VideoEditorExportRequest.canUseLegacySingleInputBackground(): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        needsBlurredBackground() &&
        captionTrack == null

private val VideoEditorExportRequest.trimDurationMs: Long
    get() = (trimEndMs - trimStartMs).coerceAtLeast(MinimumTrimMs)

private fun VideoEditorExportRequest.shouldDownsampleBeforeTransformer(): Boolean {
    if (canUseLegacySingleInputBackground()) {
        return false
    }
    val needsVideoComposition = needsBlurredBackground() || cropRect != null || captionTrack != null || shouldDownsampleFrameRate()
    if (!needsVideoComposition || sourceWidth <= 0 || sourceHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) {
        return false
    }
    val sourcePixels = sourceWidth.toLong() * sourceHeight.toLong()
    val outputPixels = outputWidth.toLong() * outputHeight.toLong()
    val exceedsOutputEnvelope = sourceWidth > outputWidth || sourceHeight > outputHeight
    val sourceMuchLargerThanOutput = sourcePixels > outputPixels * 2L
    val sourceBitrateTooHigh = sourceBitrate?.let { it > exportProfile.intermediateBitrate.toLong() * 2L } == true
    return exceedsOutputEnvelope || sourceMuchLargerThanOutput || sourceBitrateTooHigh || shouldDownsampleFrameRate()
}

private fun VideoEditorExportRequest.isWithinUploadSizeLimit(): Boolean {
    if (sourceWidth <= 0 || sourceHeight <= 0) return false
    return sourceWidth <= outputWidth && sourceHeight <= outputHeight
}

private fun VideoEditorExportRequest.downsampledIntermediateSize(): Pair<Int, Int> {
    val safeWidth = sourceWidth.takeIf { it > 0 } ?: outputWidth
    val safeHeight = sourceHeight.takeIf { it > 0 } ?: outputHeight
    val scale = minOf(
        outputWidth.toFloat() / safeWidth.toFloat(),
        outputHeight.toFloat() / safeHeight.toFloat(),
        1f
    )
    return (safeWidth * scale).roundToInt().coerceAtLeast(1) to
        (safeHeight * scale).roundToInt().coerceAtLeast(1)
}

private fun VideoEditorExportRequest.foregroundScale(): Float {
    val crop = cropRect ?: NormalizedCropRect.Full
    val safeSourceWidth = sourceWidth.takeIf { it > 0 } ?: outputWidth
    val safeSourceHeight = sourceHeight.takeIf { it > 0 } ?: outputHeight
    val croppedWidth = (safeSourceWidth * crop.width).coerceAtLeast(1f)
    val croppedHeight = (safeSourceHeight * crop.height).coerceAtLeast(1f)
    return minOf(
        outputWidth.toFloat() / croppedWidth,
        outputHeight.toFloat() / croppedHeight
    ).coerceAtLeast(0.01f)
}

private fun VideoEditorExportRequest.shouldDownsampleFrameRate(): Boolean {
    val sourceRate = sourceFrameRate ?: return false
    val targetRate = exportProfile.maxFrameRate.toFloat()
    return targetRate == EditorTargetFrameRate.toFloat() &&
        sourceRate in SourceSixtyFpsLowerBound..SourceSixtyFpsUpperBound
}

private fun VideoEditorExportRequest.frameRateEffects(): List<Effect> {
    val sourceRate = sourceFrameRate ?: return emptyList()
    if (!shouldDownsampleFrameRate()) return emptyList()
    return listOf(
        FrameDropEffect.createSimpleFrameDropEffect(
            sourceRate,
            exportProfile.maxFrameRate.toFloat()
        )
    )
}

private fun EditedMediaItem.Builder.applyOutputFrameRateIfNeeded(
    request: VideoEditorExportRequest
): EditedMediaItem.Builder =
    apply {
        if (request.shouldDownsampleFrameRate()) {
            setFrameRate(request.exportProfile.maxFrameRate)
        }
    }

private fun Transformer.Builder.configureVideoEditorFrameProcessing(
    request: VideoEditorExportRequest
): Transformer.Builder =
    setVideoFrameProcessorFactory(request.videoEditorFrameProcessorFactory())

private fun VideoEditorExportRequest.videoEditorFrameProcessorFactory(): VideoFrameProcessor.Factory {
    val baseFactory = DefaultVideoFrameProcessor.Factory.Builder()
        .apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                setGlObjectsProvider(LegacySafeGlObjectsProvider())
            }
        }
        .build()
    return if (shouldDownsampleFrameRate()) {
        AutomaticFrameRegistrationVideoFrameProcessorFactory(baseFactory)
    } else {
        baseFactory
    }
}

private class AutomaticFrameRegistrationVideoFrameProcessorFactory(
    private val delegateFactory: VideoFrameProcessor.Factory
) : VideoFrameProcessor.Factory {
    override fun create(
        context: Context,
        debugViewProvider: DebugViewProvider,
        outputColorInfo: ColorInfo,
        renderFramesAutomatically: Boolean,
        listenerExecutor: Executor,
        listener: VideoFrameProcessor.Listener
    ): VideoFrameProcessor =
        AutomaticFrameRegistrationVideoFrameProcessor(
            delegateFactory.create(
                context,
                debugViewProvider,
                outputColorInfo,
                renderFramesAutomatically,
                listenerExecutor,
                listener
            )
        )
}

private class AutomaticFrameRegistrationVideoFrameProcessor(
    private val delegate: VideoFrameProcessor
) : VideoFrameProcessor {
    override fun queueInputBitmap(inputBitmap: Bitmap, timestampIterator: TimestampIterator): Boolean =
        delegate.queueInputBitmap(inputBitmap, timestampIterator)

    override fun queueInputTexture(inputTexId: Int, presentationTimeUs: Long): Boolean =
        delegate.queueInputTexture(inputTexId, presentationTimeUs)

    override fun setOnInputFrameProcessedListener(listener: OnInputFrameProcessedListener) {
        delegate.setOnInputFrameProcessedListener(listener)
    }

    override fun setOnInputSurfaceReadyListener(listener: Runnable) {
        delegate.setOnInputSurfaceReadyListener(listener)
    }

    override fun getInputSurface(): Surface =
        delegate.inputSurface

    override fun registerInputStream(inputType: Int, effects: List<Effect>, frameInfo: FrameInfo) {
        val automaticInputType = if (inputType == VideoFrameProcessor.INPUT_TYPE_SURFACE) {
            VideoFrameProcessor.INPUT_TYPE_SURFACE_AUTOMATIC_FRAME_REGISTRATION
        } else {
            inputType
        }
        delegate.registerInputStream(automaticInputType, effects, frameInfo)
    }

    override fun registerInputFrame(): Boolean =
        delegate.registerInputFrame()

    override fun getPendingInputFrameCount(): Int =
        delegate.pendingInputFrameCount

    override fun setOutputSurfaceInfo(outputSurfaceInfo: SurfaceInfo?) {
        delegate.setOutputSurfaceInfo(outputSurfaceInfo)
    }

    override fun renderOutputFrame(renderTimeNs: Long) {
        delegate.renderOutputFrame(renderTimeNs)
    }

    override fun signalEndOfInput() {
        delegate.signalEndOfInput()
    }

    override fun flush() {
        delegate.flush()
    }

    override fun release() {
        delegate.release()
    }
}

private class LegacyBlurredFitEffect(
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val rotationDegrees: Int,
    private val displayInputWidth: Int,
    private val displayInputHeight: Int,
    private val foregroundCropRect: NormalizedCropRect = NormalizedCropRect.Full,
    private val backgroundCropRect: NormalizedCropRect = NormalizedCropRect.Full
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        LegacyBlurredFitShaderProgram(
            outputWidth = outputWidth,
            outputHeight = outputHeight,
            rotationDegrees = rotationDegrees,
            displayInputWidth = displayInputWidth,
            displayInputHeight = displayInputHeight,
            foregroundCropRect = foregroundCropRect,
            backgroundCropRect = backgroundCropRect,
            useHdr = useHdr
        )
}

private class LegacyBlurredFitShaderProgram(
    private val outputWidth: Int,
    private val outputHeight: Int,
    rotationDegrees: Int,
    private val displayInputWidth: Int,
    private val displayInputHeight: Int,
    private val foregroundCropRect: NormalizedCropRect,
    private val backgroundCropRect: NormalizedCropRect,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {
    private val normalizedRotation = rotationDegrees.normalizedVideoRotation()
    private val glProgram: GlProgram = try {
        GlProgram(LegacyBlurredFitVertexShader, LegacyBlurredFitFragmentShader)
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException.from(e)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): GlSize {
        val swapsAxes = normalizedRotation == 90 || normalizedRotation == 270
        val fallbackDisplayInputWidth = if (swapsAxes) inputHeight else inputWidth
        val fallbackDisplayInputHeight = if (swapsAxes) inputWidth else inputHeight
        val orientedInputWidth = displayInputWidth.takeIf { it > 0 } ?: fallbackDisplayInputWidth
        val orientedInputHeight = displayInputHeight.takeIf { it > 0 } ?: fallbackDisplayInputHeight
        glProgram.setFloatsUniform(
            "uOrientedInputSize",
            floatArrayOf(orientedInputWidth.toFloat(), orientedInputHeight.toFloat())
        )
        glProgram.setFloatsUniform(
            "uOutputSize",
            floatArrayOf(outputWidth.toFloat(), outputHeight.toFloat())
        )
        glProgram.setFloatsUniform("uForegroundCrop", foregroundCropRect.toShaderCropArray())
        glProgram.setFloatsUniform("uBackgroundCrop", backgroundCropRect.toShaderCropArray())
        glProgram.setIntUniform("uRotationDegrees", normalizedRotation)
        glProgram.setBufferAttribute(
            "aFramePosition",
            GlUtil.getNormalizedCoordinateBounds(),
            GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
        )
        return GlSize(outputWidth, outputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(
                GLES20.GL_TRIANGLE_STRIP,
                /* first= */ 0,
                /* count= */ 4
            )
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        // Legacy emulator EGL can crash inside glDeleteFramebuffers after Transformer completes.
        // This export-only shader lets the GL context own the tiny temporary texture pool instead.
        runCatching { glProgram.delete() }
    }
}

private fun NormalizedCropRect.toShaderCropArray(): FloatArray =
    floatArrayOf(left, top, width.coerceAtLeast(0.001f), height.coerceAtLeast(0.001f))

private const val LegacyBlurredFitVertexShader = """
attribute vec4 aFramePosition;
varying vec2 vOutputUv;

void main() {
    gl_Position = aFramePosition;
    vOutputUv = (aFramePosition.xy + 1.0) * 0.5;
}
"""

private const val LegacyBlurredFitFragmentShader = """
precision mediump float;

uniform sampler2D uTexSampler;
uniform vec2 uOrientedInputSize;
uniform vec2 uOutputSize;
uniform vec4 uForegroundCrop;
uniform vec4 uBackgroundCrop;
uniform int uRotationDegrees;
varying vec2 vOutputUv;

float cropAspect(vec4 crop) {
    return (uOrientedInputSize.x * max(crop.z, 0.001)) /
        (uOrientedInputSize.y * max(crop.w, 0.001));
}

vec2 cropCoord(vec2 localUv, vec4 crop) {
    return vec2(crop.x + localUv.x * crop.z, crop.y + localUv.y * crop.w);
}

vec2 fitLocalCoord(vec2 uv, vec4 crop) {
    float inputAspect = cropAspect(crop);
    float outputAspect = uOutputSize.x / uOutputSize.y;
    if (inputAspect > outputAspect) {
        float displayedHeight = outputAspect / inputAspect;
        return vec2(uv.x, (uv.y - ((1.0 - displayedHeight) * 0.5)) / displayedHeight);
    }
    float displayedWidth = inputAspect / outputAspect;
    return vec2((uv.x - ((1.0 - displayedWidth) * 0.5)) / displayedWidth, uv.y);
}

vec2 fillLocalCoord(vec2 uv, vec4 crop) {
    float inputAspect = cropAspect(crop);
    float outputAspect = uOutputSize.x / uOutputSize.y;
    if (inputAspect > outputAspect) {
        float visibleWidth = outputAspect / inputAspect;
        return vec2(((1.0 - visibleWidth) * 0.5) + (uv.x * visibleWidth), uv.y);
    }
    float visibleHeight = inputAspect / outputAspect;
    return vec2(uv.x, ((1.0 - visibleHeight) * 0.5) + (uv.y * visibleHeight));
}

vec2 orientCoord(vec2 uv) {
    vec2 clampedUv = clamp(uv, 0.0, 1.0);
    if (uRotationDegrees == 90) {
        return vec2(1.0 - clampedUv.y, clampedUv.x);
    }
    if (uRotationDegrees == 180) {
        return vec2(1.0 - clampedUv.x, 1.0 - clampedUv.y);
    }
    if (uRotationDegrees == 270) {
        return vec2(clampedUv.y, 1.0 - clampedUv.x);
    }
    return clampedUv;
}

vec4 sampleClamped(vec2 uv) {
    return texture2D(uTexSampler, orientCoord(uv));
}

vec2 clampCropCoord(vec2 uv, vec4 crop) {
    return vec2(
        clamp(uv.x, crop.x, crop.x + crop.z),
        clamp(uv.y, crop.y, crop.y + crop.w)
    );
}

vec4 sampleCropClamped(vec2 uv, vec4 crop) {
    return sampleClamped(clampCropCoord(uv, crop));
}

vec4 blurredBackground(vec2 uv, vec4 crop) {
    vec2 texel = 1.0 / uOrientedInputSize;
    vec4 color = vec4(0.0);
    color += sampleCropClamped(uv + texel * vec2(-72.0, -72.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-36.0, -72.0), crop);
    color += sampleCropClamped(uv + texel * vec2(  0.0, -72.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 36.0, -72.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 72.0, -72.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-72.0, -36.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-36.0, -36.0), crop);
    color += sampleCropClamped(uv + texel * vec2(  0.0, -36.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 36.0, -36.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 72.0, -36.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-72.0,   0.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-36.0,   0.0), crop);
    color += sampleCropClamped(uv, crop);
    color += sampleCropClamped(uv + texel * vec2( 36.0,   0.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 72.0,   0.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-72.0,  36.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-36.0,  36.0), crop);
    color += sampleCropClamped(uv + texel * vec2(  0.0,  36.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 36.0,  36.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 72.0,  36.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-72.0,  72.0), crop);
    color += sampleCropClamped(uv + texel * vec2(-36.0,  72.0), crop);
    color += sampleCropClamped(uv + texel * vec2(  0.0,  72.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 36.0,  72.0), crop);
    color += sampleCropClamped(uv + texel * vec2( 72.0,  72.0), crop);
    color /= 25.0;
    color.rgb *= 0.72;
    color.a = 1.0;
    return color;
}

void main() {
    vec2 backgroundUv = cropCoord(fillLocalCoord(vOutputUv, uBackgroundCrop), uBackgroundCrop);
    vec4 background = blurredBackground(backgroundUv, uBackgroundCrop);
    vec2 foregroundLocalUv = fitLocalCoord(vOutputUv, uForegroundCrop);
    bool insideForeground =
        foregroundLocalUv.x >= 0.0 &&
        foregroundLocalUv.x <= 1.0 &&
        foregroundLocalUv.y >= 0.0 &&
        foregroundLocalUv.y <= 1.0;
    vec2 foregroundUv = cropCoord(foregroundLocalUv, uForegroundCrop);
    gl_FragColor = insideForeground ? sampleClamped(foregroundUv) : background;
}
"""

private class LegacySafeGlObjectsProvider : GlObjectsProvider {
    private val createdEglContexts = mutableListOf<EGLContext>()

    override fun createEglContext(
        eglDisplay: EGLDisplay,
        openGlVersion: Int,
        configAttributes: IntArray
    ): EGLContext {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && openGlVersion > 2) {
            throw GlUtil.GlException("OpenGL ES 3 is disabled for legacy video export")
        }
        val eglConfig = chooseEglConfig(eglDisplay, configAttributes)
        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION,
            openGlVersion,
            EGL14.EGL_NONE
        )
        val eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            contextAttributes,
            0
        )
        val eglError = EGL14.eglGetError()
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT || eglError != EGL14.EGL_SUCCESS) {
            throw GlUtil.GlException(
                "eglCreateContext failed without terminating display, error code: 0x${eglError.toString(16)}"
            )
        }
        createdEglContexts += eglContext
        return eglContext
    }

    override fun createEglSurface(
        eglDisplay: EGLDisplay,
        surface: Any,
        colorTransfer: Int,
        isEncoderInputSurface: Boolean
    ): EGLSurface =
        GlUtil.createEglSurface(eglDisplay, surface, colorTransfer, isEncoderInputSurface)

    override fun createFocusedPlaceholderEglSurface(
        eglContext: EGLContext,
        eglDisplay: EGLDisplay
    ): EGLSurface =
        GlUtil.createFocusedPlaceholderEglSurface(eglContext, eglDisplay)

    override fun createBuffersForTexture(texId: Int, width: Int, height: Int): GlTextureInfo {
        val fboId = GlUtil.createFboForTexture(texId)
        return GlTextureInfo(texId, fboId, C.INDEX_UNSET, width, height)
    }

    override fun release(eglDisplay: EGLDisplay) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return
        runCatching {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
        }
        createdEglContexts.forEach { eglContext ->
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                runCatching { EGL14.eglDestroyContext(eglDisplay, eglContext) }
            }
        }
        createdEglContexts.clear()
        runCatching { EGL14.eglReleaseThread() }
    }

    private fun chooseEglConfig(eglDisplay: EGLDisplay, attributes: IntArray): EGLConfig {
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        val success = EGL14.eglChooseConfig(
            eglDisplay,
            attributes,
            0,
            configs,
            0,
            configs.size,
            numConfigs,
            0
        )
        val eglError = EGL14.eglGetError()
        val eglConfig = configs.firstOrNull()
        if (!success || numConfigs[0] <= 0 || eglConfig == null || eglError != EGL14.EGL_SUCCESS) {
            throw GlUtil.GlException(
                "eglChooseConfig failed without terminating display, error code: 0x${eglError.toString(16)}"
            )
        }
        return eglConfig
    }
}

private class NineSixteenVideoCompositorSettings(
    private val foregroundScale: Float,
    private val outputWidth: Int,
    private val outputHeight: Int
) : VideoCompositorSettings {
    override fun getOutputSize(inputSizes: MutableList<Media3Size>): Media3Size {
        return Media3Size(outputWidth, outputHeight)
    }

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings =
        if (inputId == EditorForegroundInputId) {
            OverlaySettings.Builder()
                .setScale(foregroundScale, foregroundScale)
                .build()
        } else {
            OverlaySettings.Builder()
                .setScale(EditorBackgroundCompositorScale, EditorBackgroundCompositorScale)
                .build()
        }
}

private fun NormalizedCropRect.toMedia3Crop(): Crop {
    val leftNdc = (left * 2f - 1f).coerceIn(-1f, 0.98f)
    val rightNdc = (right * 2f - 1f).coerceIn(leftNdc + 0.01f, 1f)
    val topNdc = (1f - top * 2f).coerceIn(-0.98f, 1f)
    val bottomNdc = (1f - bottom * 2f).coerceIn(-1f, topNdc - 0.01f)
    return Crop(leftNdc, rightNdc, bottomNdc, topNdc)
}

private fun NormalizedCropRect.displayAspectRatio(sourceAspectRatio: Float): Float =
    (sourceAspectRatio * width.coerceAtLeast(0.01f) / height.coerceAtLeast(0.01f)).coerceAtLeast(0.1f)

private fun NormalizedCropRect.centerCropToAspect(
    targetAspectRatio: Float,
    sourceAspectRatio: Float
): NormalizedCropRect {
    val safeSourceAspect = sourceAspectRatio.coerceAtLeast(0.1f)
    val safeTargetAspect = targetAspectRatio.coerceAtLeast(0.1f)
    val cropAspect = displayAspectRatio(safeSourceAspect)
    var nextWidth = width
    var nextHeight = height
    if (cropAspect > safeTargetAspect) {
        nextWidth = (safeTargetAspect * nextHeight / safeSourceAspect).coerceAtMost(width)
    } else if (cropAspect < safeTargetAspect) {
        nextHeight = (safeSourceAspect * nextWidth / safeTargetAspect).coerceAtMost(height)
    }
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val clampedCenter = clampCropCenter(nextWidth, nextHeight, Offset(centerX, centerY))
    val result = NormalizedCropRect(
        left = clampedCenter.x - nextWidth / 2f,
        top = clampedCenter.y - nextHeight / 2f,
        right = clampedCenter.x + nextWidth / 2f,
        bottom = clampedCenter.y + nextHeight / 2f
    )
    return result
}

private fun Int.normalizedVideoRotation(): Int {
    val normalized = ((this % 360) + 360) % 360
    return if (normalized == 90 || normalized == 180 || normalized == 270) normalized else 0
}

private fun Int.videoRotationCorrectionDegrees(): Int {
    val normalizedRotation = normalizedVideoRotation()
    return normalizedRotation
}

private fun NormalizedCropRect.requiresLegacyBitmapPlayback(rotationDegrees: Int): Boolean {
    if (isFullFrame || Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return false
    val rotation = rotationDegrees.normalizedVideoRotation()
    return rotation == 90 || rotation == 270
}

private fun TextureView.applyVideoEditorPlaybackTransform(
    crop: NormalizedCropRect,
    rotationDegrees: Int
) {
    fun applyTransform() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return
        val rotation = rotationDegrees.normalizedVideoRotation()
        if (crop.isFullFrame && (rotation == 90 || rotation == 270)) {
            val viewRect = RectF(0f, 0f, viewWidth, viewHeight)
            val bufferRect = RectF(0f, 0f, viewHeight, viewWidth)
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val matrix = Matrix().apply {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postRotate(rotation.toFloat(), centerX, centerY)
            }
            setTransform(matrix)
            invalidate()
            return
        }
        if (crop.isFullFrame && rotation == 180) {
            val matrix = Matrix().apply {
                postRotate(180f, viewWidth / 2f, viewHeight / 2f)
            }
            setTransform(matrix)
            invalidate()
            return
        }
        val safeWidth = crop.width.coerceAtLeast(0.01f)
        val safeHeight = crop.height.coerceAtLeast(0.01f)
        val scaleX = 1f / safeWidth
        val scaleY = 1f / safeHeight
        val translateX = -crop.left * viewWidth * scaleX
        val translateY = -crop.top * viewHeight * scaleY
        val matrix = Matrix().apply {
            setScale(scaleX, scaleY)
            postTranslate(translateX, translateY)
        }
        setTransform(matrix)
        invalidate()
    }
    if (width > 0 && height > 0) {
        applyTransform()
    } else {
        post { applyTransform() }
    }
}

private fun Long.formatVideoTime(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun rememberVideoPosterFrame(uri: Uri, rotationDegrees: Int): Bitmap? {
    val context = LocalContext.current
    val displayRotation = rotationDegrees.normalizedVideoRotation()
    var frame by remember(uri, displayRotation) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, displayRotation) {
        frame = withContext(Dispatchers.IO) {
            runCatching {
                withVideoMetadataRetriever { retriever ->
                    retriever.setSource(context, uri)
                    retriever.getScaledVideoFrameAtTime(
                        timeUs = 0L,
                        option = MediaMetadataRetriever.OPTION_CLOSEST,
                        maxDimension = PreviewPosterMaxDimension
                    )
                }
            }.getOrNull()
        }
    }
    return frame
}

@Composable
private fun rememberVideoPreviewFrame(
    uri: Uri,
    positionMs: Long,
    enabled: Boolean,
    rotationDegrees: Int
): Bitmap? {
    val context = LocalContext.current
    val displayRotation = rotationDegrees.normalizedVideoRotation()
    val frameBucket = positionMs / PreviewPlaybackFrameIntervalMs
    var frame by remember(uri, displayRotation) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, frameBucket, enabled, displayRotation) {
        if (!enabled) {
            frame = null
            return@LaunchedEffect
        }
        frame = withContext(Dispatchers.IO) {
            runCatching {
                withVideoMetadataRetriever { retriever ->
                    retriever.setSource(context, uri)
                    retriever.getScaledVideoFrameAtTime(
                        timeUs = positionMs * 1000L,
                        option = MediaMetadataRetriever.OPTION_CLOSEST,
                        maxDimension = PreviewPosterMaxDimension
                    )
                }
            }.getOrNull()
        }
    }
    return frame
}

private fun VideoCropMode.cropRect(
    videoAspect: Float,
    zoom: Float,
    center: Offset
): NormalizedCropRect {
    val aspect = targetAspect ?: return NormalizedCropRect.Full
    val safeVideoAspect = videoAspect.coerceAtLeast(0.1f)
    var width = 1f
    var height = safeVideoAspect / aspect
    if (height > 1f) {
        height = 1f
        width = aspect / safeVideoAspect
    }
    val safeZoom = zoom.coerceIn(1f, 3f)
    width = (width / safeZoom).coerceIn(0.12f, 1f)
    height = (height / safeZoom).coerceIn(0.12f, 1f)
    val clampedCenter = clampCropCenter(width, height, center)
    return NormalizedCropRect(
        left = clampedCenter.x - width / 2f,
        top = clampedCenter.y - height / 2f,
        right = clampedCenter.x + width / 2f,
        bottom = clampedCenter.y + height / 2f
    )
}

private fun VideoCropMode.clampCenter(
    videoAspect: Float,
    zoom: Float,
    center: Offset
): Offset {
    val rect = cropRect(videoAspect, zoom, Offset(0.5f, 0.5f))
    return clampCropCenter(rect.width, rect.height, center)
}

private fun clampCropCenter(width: Float, height: Float, center: Offset): Offset =
    Offset(
        x = center.x.coerceIn(width / 2f, 1f - width / 2f),
        y = center.y.coerceIn(height / 2f, 1f - height / 2f)
    )

private enum class VideoCropMode(
    @param:StringRes val labelRes: Int,
    val targetAspect: Float?
) {
    Original(R.string.video_editor_crop_original, null),
    Square(R.string.video_editor_crop_square, 1f),
    FourFive(R.string.video_editor_crop_four_five, 4f / 5f),
    Portrait(R.string.video_editor_crop_portrait, 9f / 16f),
    Landscape(R.string.video_editor_crop_landscape, 16f / 9f)
}

private enum class TimelineMarker {
    Start,
    End
}

private data class VideoEditorMetadata(
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val bitrate: Long? = null,
    val frameRate: Float? = null
) {
    val displayWidth: Int
        get() {
            val normalizedRotation = rotation.normalizedVideoRotation()
            val rotated = normalizedRotation == 90 || normalizedRotation == 270
            return if (rotated) height else width
        }

    val displayHeight: Int
        get() {
            val normalizedRotation = rotation.normalizedVideoRotation()
            val rotated = normalizedRotation == 90 || normalizedRotation == 270
            return if (rotated) width else height
        }

    val aspectRatio: Float?
        get() {
            if (displayWidth <= 0 || displayHeight <= 0) return null
            return displayWidth.toFloat() / displayHeight.toFloat()
        }

    fun hasNineSixteenAspect(): Boolean =
        aspectRatio?.let { abs(it - EditorOutputAspectRatio) <= DirectStreamAspectTolerance } == true
}

private data class VideoEditorExportRequest(
    val sourceUri: Uri,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val sourceDurationMs: Long,
    val removeAudio: Boolean,
    val cropRect: NormalizedCropRect?,
    val backgroundCropRect: NormalizedCropRect?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val sourceRotation: Int,
    val sourceFrameRate: Float?,
    val sourceBitrate: Long?,
    val outputWidth: Int,
    val outputHeight: Int,
    val exportProfile: VideoExportProfile,
    val captionTrack: CaptionBurnInTrack?
)

private data class NormalizedCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isFullFrame: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    companion object {
        val Full = NormalizedCropRect(0f, 0f, 1f, 1f)
    }
}

private const val TimelineFrameCount = 6
private const val TimelineFrameMaxDimension = 120
private const val TimelineFrameLoadDelayMs = 350L
private const val PreviewPosterMaxDimension = 480
private const val PreviewPlaybackFrameIntervalMs = 120L
private const val MinimumTrimMs = 500L
private const val MaximumTrimDurationMs = 15 * 60 * 1000L
private const val TransformerCompletionFallbackProgress = 95
private const val TransformerStableOutputCompletionMs = 3_500L
private const val TransformerStableOutputMinBytes = 128 * 1024L
private const val DownsampleExportProgressShare = 0.28f
private const val EditorTargetFrameRate = 30
private const val SourceSixtyFpsLowerBound = 50f
private const val SourceSixtyFpsUpperBound = 70f
private const val VideoEditorForceGlBrightness = 0.0001f
private const val CaptionPreviewWidth = 540
private const val CaptionPreviewHeight = 960
private const val EditorForegroundInputId = 0
private const val EditorBackgroundInputId = 1
private const val EditorOutputAspectRatio = 9f / 16f
private const val EditorBackgroundBlurSigma = 18f
private const val EditorBackgroundCompositorScale = 1f
private const val DirectStreamAspectTolerance = 0.01f
private const val DirectStreamCopyDefaultBufferSize = 1 * 1024 * 1024
private const val DirectStreamProgressIntervalMs = 250L
private const val DirectOriginalTrimToleranceMs = 80L
private const val DirectStreamAudioBitrateAllowance = 160_000L
private const val LegacyDirectStreamShortSideLimit = 720
private const val LegacyDirectStreamLongSideLimit = 1280
private const val VideoEditorLogTag = "QuataVideoEditor"
