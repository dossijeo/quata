@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.postcomposer.videoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.effect.Crop
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.VideoCompositorSettings
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.Size as Media3Size
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
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
import com.quata.core.designsystem.theme.QuataBackground
import com.quata.core.designsystem.theme.QuataDivider
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.designsystem.theme.QuataSurfaceAlt
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
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
    val appContext = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val metadata = rememberVideoEditorMetadata(videoUri)
    val durationMs = metadata.durationMs
    val videoAspect = metadata.aspectRatio ?: (9f / 16f)
    val frames = rememberTimelineFrames(videoUri, durationMs)

    var trimStartMs by remember(videoUri) { mutableLongStateOf(0L) }
    var trimEndMs by remember(videoUri) { mutableLongStateOf(0L) }
    var durationApplied by remember(videoUri) { mutableStateOf(false) }
    var isMuted by remember(videoUri) { mutableStateOf(false) }
    var isCropPanelOpen by remember(videoUri) { mutableStateOf(false) }
    var cropMode by remember(videoUri) { mutableStateOf(VideoCropMode.Original) }
    var cropZoom by remember(videoUri) { mutableFloatStateOf(1f) }
    var cropCenter by remember(videoUri) { mutableStateOf(Offset(0.5f, 0.5f)) }
    var isExporting by remember(videoUri) { mutableStateOf(false) }
    var exportProgress by remember(videoUri) { mutableFloatStateOf(0f) }
    var exportError by remember(videoUri) { mutableStateOf<String?>(null) }

    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    val backgroundPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            prepare()
        }
    }
    var isPlaying by remember(player) { mutableStateOf(false) }
    var currentPositionMs by remember(player) { mutableLongStateOf(0L) }
    val posterFrame = rememberVideoPosterFrame(videoUri) ?: frames.firstOrNull()

    val cropRect = remember(cropMode, cropZoom, cropCenter, videoAspect) {
        cropMode.cropRect(videoAspect, cropZoom, cropCenter)
    }

    LaunchedEffect(cropMode, cropZoom, cropCenter, cropRect, isCropPanelOpen, videoAspect) {
        Log.d(
            VideoEditorLogTag,
            "cropState mode=$cropMode zoom=${cropZoom.formatLog()} center=${cropCenter.formatLog()} " +
                "rect=${cropRect.formatLog()} panelOpen=$isCropPanelOpen sourceAspect=${videoAspect.formatLog()}"
        )
    }

    LaunchedEffect(durationMs) {
        if (!durationApplied && durationMs > 0L) {
            trimStartMs = 0L
            trimEndMs = durationMs
            durationApplied = true
        }
    }

    LaunchedEffect(isMuted, player) {
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(backgroundPlayer) {
        backgroundPlayer.volume = 0f
    }

    LaunchedEffect(backgroundPlayer) {
        backgroundPlayer.setVideoEffects(emptyList())
    }

    LaunchedEffect(player, cropRect, isCropPanelOpen) {
        val foregroundEffects = emptyList<Effect>()
        Log.d(
            VideoEditorLogTag,
            "foregroundEffects crop=${cropRect.formatLog()} applied=${foregroundEffects.isNotEmpty()} panelOpen=$isCropPanelOpen"
        )
        player.setVideoEffects(foregroundEffects)
    }

    LaunchedEffect(player, backgroundPlayer, trimStartMs, trimEndMs) {
        while (true) {
            val position = player.currentPosition.coerceAtLeast(0L)
            currentPositionMs = position
            if (trimEndMs > trimStartMs && position >= trimEndMs) {
                player.pause()
                player.seekTo(trimStartMs)
                backgroundPlayer.pause()
                backgroundPlayer.seekTo(trimStartMs)
                currentPositionMs = trimStartMs
            }
            val backgroundPosition = backgroundPlayer.currentPosition.coerceAtLeast(0L)
            if (abs(backgroundPosition - player.currentPosition) > PreviewSyncToleranceMs) {
                backgroundPlayer.seekTo(player.currentPosition.coerceAtLeast(0L))
            }
            when {
                player.isPlaying && !backgroundPlayer.isPlaying -> backgroundPlayer.play()
                !player.isPlaying && backgroundPlayer.isPlaying -> backgroundPlayer.pause()
            }
            delay(120L)
        }
    }

    DisposableEffect(player, backgroundPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
                if (isPlayingNow) {
                    backgroundPlayer.seekTo(player.currentPosition.coerceAtLeast(0L))
                    backgroundPlayer.play()
                } else {
                    backgroundPlayer.pause()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
            backgroundPlayer.release()
        }
    }

    fun playOrPause() {
        if (player.isPlaying) {
            player.pause()
            backgroundPlayer.pause()
        } else {
            val position = player.currentPosition
            if (position < trimStartMs || position >= trimEndMs) {
                player.seekTo(trimStartMs)
                backgroundPlayer.seekTo(trimStartMs)
            } else {
                backgroundPlayer.seekTo(position.coerceAtLeast(0L))
            }
            player.play()
            backgroundPlayer.play()
        }
    }

    fun seekPreviewTo(targetMs: Long) {
        if (durationMs <= 0L) return
        val boundedTarget = targetMs.coerceIn(0L, durationMs)
        player.pause()
        player.seekTo(boundedTarget)
        backgroundPlayer.pause()
        backgroundPlayer.seekTo(boundedTarget)
        currentPositionMs = boundedTarget
    }

    fun export() {
        if (isExporting || durationMs <= 0L || trimEndMs <= trimStartMs) return
        player.pause()
        exportError = null
        isCropPanelOpen = false
        isExporting = true
        exportProgress = 0f
        val request = VideoEditorExportRequest(
            sourceUri = videoUri,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            removeAudio = isMuted,
            cropRect = cropRect.takeUnless { it.isFullFrame },
            backgroundCropRect = cropRect
                .centerCropToAspect(EditorOutputAspectRatio, videoAspect)
                .takeUnless { it.isFullFrame },
            sourceWidth = metadata.displayWidth,
            sourceHeight = metadata.displayHeight
        )
        scope.launch {
            runCatching {
                appContext.exportEditedVideo(request) { progress ->
                    exportProgress = progress
                }
            }.onSuccess { uri ->
                isExporting = false
                onExported(uri)
            }.onFailure { throwable ->
                isExporting = false
                exportError = throwable.message ?: appContext.getString(R.string.video_editor_export_failed)
            }
        }
    }

    BackHandler(enabled = !isExporting, onBack = onDismiss)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = QuataBackground
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 56.dp)
        ) {
            VideoEditorTopBar(
                isMuted = isMuted,
                isCropPanelOpen = isCropPanelOpen,
                isExporting = isExporting,
                onBack = onDismiss,
                onToggleMute = { isMuted = !isMuted },
                onToggleCrop = { isCropPanelOpen = !isCropPanelOpen },
                onExport = ::export
            )

            VideoPreviewPane(
                player = player,
                backgroundPlayer = backgroundPlayer,
                aspectRatio = videoAspect,
                posterFrame = posterFrame,
                showPoster = posterFrame != null && !isPlaying && currentPositionMs <= 50L,
                cropRect = cropRect,
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
                currentPositionMs = currentPositionMs,
                isExporting = isExporting,
                onTrimStartChange = { next ->
                    val boundedStart = next.coerceIn(0L, (trimEndMs - MinimumTrimMs).coerceAtLeast(0L))
                    trimStartMs = boundedStart
                    seekPreviewTo(boundedStart)
                },
                onTrimEndChange = { next ->
                    val boundedEnd = next.coerceIn((trimStartMs + MinimumTrimMs).coerceAtMost(durationMs), durationMs)
                    trimEndMs = boundedEnd
                    seekPreviewTo((boundedEnd - 33L).coerceAtLeast(trimStartMs))
                },
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

            VideoEditorInfoBar(
                currentPositionMs = currentPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)),
                selectedDurationMs = (trimEndMs - trimStartMs).coerceAtLeast(0L),
                isPlaying = isPlaying,
                isExporting = isExporting,
                exportProgress = exportProgress,
                error = exportError,
                onPlayPause = ::playOrPause
            )
        }
    }
}

@Composable
private fun VideoEditorTopBar(
    isMuted: Boolean,
    isCropPanelOpen: Boolean,
    isExporting: Boolean,
    onBack: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleCrop: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(QuataSurface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIconButton(onClick = onBack, enabled = !isExporting) {
            CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.video_editor_back), tint = Color.White)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.video_editor_title),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!isExporting) {
            VideoToolButton(
                label = stringResource(if (isMuted) R.string.video_editor_unmute else R.string.video_editor_mute),
                enabled = true,
                onClick = onToggleMute
            ) {
                CompactIcon(if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White)
            }
            VideoToolButton(
                label = stringResource(if (isCropPanelOpen) R.string.video_editor_crop_done else R.string.video_editor_crop),
                enabled = true,
                onClick = onToggleCrop
            ) {
                CompactIcon(if (isCropPanelOpen) Icons.Filled.Check else Icons.Filled.Crop, contentDescription = null, tint = Color.White)
            }
            VideoToolButton(
                label = stringResource(R.string.video_editor_export),
                enabled = true,
                onClick = onExport,
            ) {
                CompactIcon(Icons.Filled.Save, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun VideoToolButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.42f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 66.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.08f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.78f * contentAlpha), fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun VideoPreviewPane(
    player: ExoPlayer,
    backgroundPlayer: ExoPlayer,
    aspectRatio: Float,
    posterFrame: Bitmap?,
    showPoster: Boolean,
    cropRect: NormalizedCropRect,
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
        val foregroundPosterFrame = remember(posterFrame, appliedCrop) {
            posterFrame?.cropNormalized(appliedCrop)
        }
        val backgroundTextureWidth = previewWidth * EditorBackgroundPreviewScale
        val backgroundTextureHeight = previewHeight * EditorBackgroundPreviewScale
        val backgroundScale = 1f / EditorBackgroundPreviewScale
        val backgroundPosterFrame = remember(posterFrame, backgroundCrop) {
            posterFrame?.cropNormalized(backgroundCrop)
        }

        LaunchedEffect(
            aspectRatio,
            isCropVisible,
            appliedCrop,
            cropRect,
            backgroundCrop,
            previewWidth,
            previewHeight,
            backgroundTextureWidth,
            backgroundTextureHeight,
            foregroundWidth,
            foregroundHeight
        ) {
            Log.d(
                VideoEditorLogTag,
                "previewGeometry visibleCrop=${appliedCrop.formatLog()} overlayCrop=${cropRect.formatLog()} " +
                    "backgroundCrop=${backgroundCrop.formatLog()} isCropVisible=$isCropVisible " +
                    "preview=${previewWidth.value.formatLog()}x${previewHeight.value.formatLog()}dp " +
                    "backgroundTexture=${backgroundTextureWidth.value.formatLog()}x${backgroundTextureHeight.value.formatLog()}dp " +
                    "fg=${foregroundWidth.value.formatLog()}x${foregroundHeight.value.formatLog()}dp " +
                    "sourceAspect=${aspectRatio.formatLog()} cropAspect=${foregroundAspectRatio.formatLog()}"
            )
        }

        Box(
            modifier = Modifier
                .size(previewWidth, previewHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { viewContext ->
                    TextureView(viewContext).apply {
                        configureVideoEditorTexture(
                            player = backgroundPlayer,
                            crop = backgroundCrop,
                            blurRadiusPx = EditorBackgroundBlurRadiusPx,
                            logLabel = "background"
                        )
                    }
                },
                update = {
                    it.configureVideoEditorTexture(
                        player = backgroundPlayer,
                        crop = backgroundCrop,
                        blurRadiusPx = EditorBackgroundBlurRadiusPx,
                        logLabel = "background"
                    )
                },
                modifier = Modifier
                    .size(backgroundTextureWidth, backgroundTextureHeight)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = backgroundScale
                        scaleY = backgroundScale
                    }
            )
            if (showPoster && backgroundPosterFrame != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(22.dp)
                        .align(Alignment.Center)
                ) {
                    Image(
                        bitmap = backgroundPosterFrame.asImageBitmap(),
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
                AndroidView(
                    factory = { viewContext ->
                        (LayoutInflater.from(viewContext)
                            .inflate(R.layout.quata_video_editor_player_texture, null, false) as PlayerView).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setKeepContentOnPlayerReset(true)
                            setEnableComposeSurfaceSyncWorkaround(true)
                            this.player = player
                            applyVideoEditorTextureCrop(appliedCrop, "foreground")
                        }
                    },
                    update = {
                        it.useController = false
                        it.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        it.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        it.setKeepContentOnPlayerReset(true)
                        it.setEnableComposeSurfaceSyncWorkaround(true)
                        if (it.player !== player) it.player = player
                        it.applyVideoEditorTextureCrop(appliedCrop, "foreground")
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (showPoster && foregroundPosterFrame != null) {
                    Image(
                        bitmap = foregroundPosterFrame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
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
    val safeDuration = durationMs.coerceAtLeast(1L)
    val startFraction = (trimStartMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val endFraction = (trimEndMs.toFloat() / safeDuration).coerceIn(startFraction, 1f)
    val currentFraction = (currentPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val handleWidth = 30.dp
    val currentStartFraction by rememberUpdatedState(startFraction)
    val currentEndFraction by rememberUpdatedState(endFraction)
    val currentOnTrimStartChange by rememberUpdatedState(onTrimStartChange)
    val currentOnTrimEndChange by rememberUpdatedState(onTrimEndChange)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val baseModifier = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(QuataSurfaceAlt)
    val interactiveModifier = if (isExporting) {
        baseModifier
    } else {
        baseModifier.pointerInput(durationMs) {
            fun positionToMs(x: Float): Long {
                val width = size.width.toFloat().coerceAtLeast(1f)
                return (x.coerceIn(0f, width) / width * safeDuration).roundToLong()
            }

            fun markerFor(x: Float): TimelineMarker {
                val width = size.width.toFloat().coerceAtLeast(1f)
                val startX = currentStartFraction * width
                val endX = currentEndFraction * width
                return if (abs(x - startX) <= abs(x - endX)) {
                    TimelineMarker.Start
                } else {
                    TimelineMarker.End
                }
            }

            fun updateTrim(marker: TimelineMarker, x: Float) {
                when (marker) {
                    TimelineMarker.Start -> currentOnTrimStartChange(positionToMs(x))
                    TimelineMarker.End -> currentOnTrimEndChange(positionToMs(x))
                }
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val marker = markerFor(down.position.x)
                val dragStart = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                    updateTrim(marker, change.position.x)
                }

                if (dragStart == null) {
                    currentOnSeek(positionToMs(down.position.x))
                } else {
                    drag(dragStart.id) { change ->
                        change.consume()
                        updateTrim(marker, change.position.x)
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = interactiveModifier
    ) {
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
                    color = QuataOrange,
                    topLeft = Offset(startX, 0f),
                    size = Size(endX - startX, size.height),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .offset(x = maxWidth * currentFraction - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.9f))
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
        }
    }
}

@Composable
private fun TimelineHandle(
    modifier: Modifier,
    alignStart: Boolean
) {
    Box(
        modifier = modifier.background(QuataOrange),
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
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(QuataSurface)
            .border(1.dp, QuataDivider, RoundedCornerShape(18.dp))
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
                    ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black)
                } else {
                    ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
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
                    color = Color.White.copy(alpha = 0.74f),
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
private fun VideoEditorInfoBar(
    currentPositionMs: Long,
    selectedDurationMs: Long,
    isPlaying: Boolean,
    isExporting: Boolean,
    exportProgress: Float,
    error: String?,
    onPlayPause: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(QuataSurface)
            .padding(horizontal = 18.dp, vertical = 6.dp)
    ) {
        if (isExporting) {
            LinearProgressIndicator(
                progress = { exportProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = QuataOrange,
                trackColor = QuataDivider
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.video_editor_exporting, (exportProgress * 100).toInt().coerceIn(0, 100)),
                color = Color.White,
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
            Surface(
                color = Color.White.copy(alpha = 0.72f),
                contentColor = Color.Black,
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
    val label = text.substringBefore(':', text).trim()
    val value = text.substringAfter(':', "").trim()
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.52f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value.ifBlank { text },
            color = Color.White.copy(alpha = 0.68f),
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
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setSource(context, uri)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    VideoEditorMetadata(duration, width, height, rotation)
                }
            }.getOrDefault(VideoEditorMetadata())
        }
    }
    return metadata
}

@Composable
private fun rememberTimelineFrames(uri: Uri, durationMs: Long): List<Bitmap> {
    val context = LocalContext.current
    var frames by remember(uri) { mutableStateOf(emptyList<Bitmap>()) }
    LaunchedEffect(uri, durationMs) {
        frames = emptyList()
        if (durationMs <= 0L) return@LaunchedEffect
        frames = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setSource(context, uri)
                    List(TimelineFrameCount) { index ->
                        val fraction = if (TimelineFrameCount == 1) 0f else index.toFloat() / (TimelineFrameCount - 1)
                        val timeUs = (durationMs * 1000L * fraction).roundToLong()
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                            frame.scaleForTimeline()
                        }
                    }.filterNotNull()
                }
            }.getOrDefault(emptyList())
        }
    }
    return frames
}

private fun MediaMetadataRetriever.setSource(context: Context, uri: Uri) {
    if (uri.scheme == "content" || uri.scheme == "file") {
        setDataSource(context, uri)
    } else {
        setDataSource(uri.toString(), emptyMap())
    }
}

private fun Bitmap.scaleForTimeline(): Bitmap {
    val targetWidth = 160
    val targetHeight = 90
    if (width <= targetWidth && height <= targetHeight) return this
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    if (scaled !== this) recycle()
    return scaled
}

private fun Bitmap.scaleForPreviewPoster(): Bitmap {
    val maxDimension = 1080
    val largestDimension = maxOf(width, height)
    if (largestDimension <= maxDimension) return this
    val scale = maxDimension.toFloat() / largestDimension.toFloat()
    val targetWidth = (width * scale).roundToLong().toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToLong().toInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    if (scaled !== this) recycle()
    return scaled
}

private fun Bitmap.cropNormalized(rect: NormalizedCropRect): Bitmap {
    if (rect.isFullFrame) return this
    val leftPx = (rect.left * width).roundToInt().coerceIn(0, width - 1)
    val topPx = (rect.top * height).roundToInt().coerceIn(0, height - 1)
    val rightPx = (rect.right * width).roundToInt().coerceIn(leftPx + 1, width)
    val bottomPx = (rect.bottom * height).roundToInt().coerceIn(topPx + 1, height)
    Log.d(
        VideoEditorLogTag,
        "posterCrop rect=${rect.formatLog()} bitmap=${width}x$height px=($leftPx,$topPx,$rightPx,$bottomPx)"
    )
    return Bitmap.createBitmap(this, leftPx, topPx, rightPx - leftPx, bottomPx - topPx)
}

private suspend fun Context.exportEditedVideo(
    request: VideoEditorExportRequest,
    onProgress: (Float) -> Unit
): Uri {
    val outputFile = withContext(Dispatchers.IO) {
        File(cacheDir, "quata-edited-video-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}.mp4")
    }
    return withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            val progressHolder = ProgressHolder()
            lateinit var transformer: Transformer
            val progressRunnable = object : Runnable {
                override fun run() {
                    if (!continuation.isActive) return
                    val progressState = transformer.getProgress(progressHolder)
                    if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(progressHolder.progress / 100f)
                    }
                    handler.postDelayed(this, 250L)
                }
            }

            val mediaItem = request.toClippedMediaItem()
            val cropEffects = request.cropRect?.let { listOf<Effect>(it.toMedia3Crop()) }.orEmpty()
            val backgroundCropEffects = request.backgroundCropRect
                ?.let { listOf<Effect>(it.toMedia3Crop()) }
                .orEmpty()
            val backgroundEffects = buildList {
                addAll(backgroundCropEffects)
                add(
                    Presentation.createForWidthAndHeight(
                        EditorOutputWidth,
                        EditorOutputHeight,
                        Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
                    )
                )
                add(GaussianBlur(EditorBackgroundBlurSigma))
            }
            Log.d(
                VideoEditorLogTag,
                "exportGeometry foreground=${(request.cropRect ?: NormalizedCropRect.Full).formatLog()} " +
                    "background=${(request.backgroundCropRect ?: NormalizedCropRect.Full).formatLog()} " +
                    "foregroundScale=${request.foregroundScale().formatLog()}"
            )
            val backgroundItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(true)
                .setEffects(Effects(emptyList(), backgroundEffects))
                .build()
            val foregroundItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(request.removeAudio)
                .setEffects(Effects(emptyList(), cropEffects))
                .build()
            val composition = Composition.Builder(
                listOf(
                    EditedMediaItemSequence.Builder(foregroundItem).build(),
                    EditedMediaItemSequence.Builder(backgroundItem).build()
                )
            )
                .setVideoCompositorSettings(NineSixteenVideoCompositorSettings(request.foregroundScale()))
                .build()
            val encoderFactory = DefaultEncoderFactory.Builder(this@exportEditedVideo)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(EditorVideoBitrate)
                        .build()
                )
                .build()
            transformer = Transformer.Builder(this@exportEditedVideo)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacks(progressRunnable)
                        onProgress(1f)
                        if (continuation.isActive) continuation.resume(Uri.fromFile(outputFile))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        handler.removeCallbacks(progressRunnable)
                        runCatching { outputFile.delete() }
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                handler.removeCallbacks(progressRunnable)
                runCatching { transformer.cancel() }
                runCatching { outputFile.delete() }
            }
            transformer.start(composition, outputFile.absolutePath)
            handler.post(progressRunnable)
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

private fun VideoEditorExportRequest.foregroundScale(): Float {
    val crop = cropRect ?: NormalizedCropRect.Full
    val safeSourceWidth = sourceWidth.takeIf { it > 0 } ?: EditorOutputWidth
    val safeSourceHeight = sourceHeight.takeIf { it > 0 } ?: EditorOutputHeight
    val croppedWidth = (safeSourceWidth * crop.width).coerceAtLeast(1f)
    val croppedHeight = (safeSourceHeight * crop.height).coerceAtLeast(1f)
    return minOf(
        EditorOutputWidth.toFloat() / croppedWidth,
        EditorOutputHeight.toFloat() / croppedHeight
    ).coerceAtLeast(0.01f)
}

private class NineSixteenVideoCompositorSettings(
    private val foregroundScale: Float
) : VideoCompositorSettings {
    private var didLogInputSizes = false

    override fun getOutputSize(inputSizes: MutableList<Media3Size>): Media3Size {
        if (!didLogInputSizes) {
            didLogInputSizes = true
            Log.d(
                VideoEditorLogTag,
                "exportCompositorSizes foreground=${inputSizes.getOrNull(EditorForegroundInputId)?.formatLog()} " +
                    "background=${inputSizes.getOrNull(EditorBackgroundInputId)?.formatLog()} " +
                    "foregroundScale=${foregroundScale.formatLog()}"
            )
        }
        return Media3Size(EditorOutputWidth, EditorOutputHeight)
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
    Log.d(
        VideoEditorLogTag,
        "media3Crop rect=${formatLog()} ndc=(${leftNdc.formatLog()},${rightNdc.formatLog()},${bottomNdc.formatLog()},${topNdc.formatLog()})"
    )
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
    Log.d(
        VideoEditorLogTag,
        "backgroundSourceCrop base=${formatLog()} targetAspect=${safeTargetAspect.formatLog()} " +
            "sourceAspect=${safeSourceAspect.formatLog()} result=${result.formatLog()}"
    )
    return result
}

private fun TextureView.configureVideoEditorTexture(
    player: ExoPlayer,
    crop: NormalizedCropRect,
    blurRadiusPx: Float,
    logLabel: String
) {
    if (getTag(R.id.quata_video_editor_background_player_tag) !== player) {
        player.setVideoTextureView(this)
        setTag(R.id.quata_video_editor_background_player_tag, player)
    }
    setOpaque(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP))
    }
    applyVideoEditorCropTransform(crop, logLabel)
}

private fun PlayerView.applyVideoEditorTextureCrop(
    crop: NormalizedCropRect,
    logLabel: String
) {
    val textureView = getVideoSurfaceView() as? TextureView
    if (textureView == null) {
        post {
            (getVideoSurfaceView() as? TextureView)?.applyVideoEditorCropTransform(crop, logLabel)
        }
        return
    }
    textureView.applyVideoEditorCropTransform(crop, logLabel)
}

private fun TextureView.applyVideoEditorCropTransform(
    crop: NormalizedCropRect,
    logLabel: String
) {
    fun applyTransform() {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return
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
        Log.d(
            VideoEditorLogTag,
            "textureCrop label=$logLabel view=${viewWidth.formatLog()}x${viewHeight.formatLog()} " +
                "crop=${crop.formatLog()} scale=${scaleX.formatLog()},${scaleY.formatLog()} " +
                "translate=${translateX.formatLog()},${translateY.formatLog()}"
        )
    }
    if (width > 0 && height > 0) {
        applyTransform()
    } else {
        post { applyTransform() }
    }
}

private fun Float.formatLog(): String = String.format(Locale.US, "%.3f", this)

private fun Media3Size.formatLog(): String = "${width}x$height"

private fun Offset.formatLog(): String = "(${x.formatLog()},${y.formatLog()})"

private fun NormalizedCropRect.formatLog(): String =
    "(l=${left.formatLog()},t=${top.formatLog()},r=${right.formatLog()},b=${bottom.formatLog()},w=${width.formatLog()},h=${height.formatLog()})"

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
private fun rememberVideoPosterFrame(uri: Uri): Bitmap? {
    val context = LocalContext.current
    var frame by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        frame = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setSource(context, uri)
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?.scaleForPreviewPoster()
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
    val rotation: Int = 0
) {
    val displayWidth: Int
        get() {
            val rotated = rotation == 90 || rotation == 270
            return if (rotated) height else width
        }

    val displayHeight: Int
        get() {
            val rotated = rotation == 90 || rotation == 270
            return if (rotated) width else height
        }

    val aspectRatio: Float?
        get() {
            if (displayWidth <= 0 || displayHeight <= 0) return null
            return displayWidth.toFloat() / displayHeight.toFloat()
        }
}

private data class VideoEditorExportRequest(
    val sourceUri: Uri,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val removeAudio: Boolean,
    val cropRect: NormalizedCropRect?,
    val backgroundCropRect: NormalizedCropRect?,
    val sourceWidth: Int,
    val sourceHeight: Int
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

private const val TimelineFrameCount = 8
private const val MinimumTrimMs = 500L
private const val EditorVideoBitrate = 2_500_000
private const val EditorOutputWidth = 1080
private const val EditorOutputHeight = 1920
private const val EditorForegroundInputId = 0
private const val EditorBackgroundInputId = 1
private const val EditorOutputAspectRatio = 9f / 16f
private const val EditorBackgroundBlurSigma = 18f
private const val EditorBackgroundBlurRadiusPx = 32f
private const val EditorBackgroundPreviewScale = 0.5f
private const val EditorBackgroundCompositorScale = 1f
private const val PreviewSyncToleranceMs = 80L
private const val VideoEditorLogTag = "QuataVideoEditor"
