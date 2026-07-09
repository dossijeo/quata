@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.core.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.quata.R
import coil.compose.AsyncImage
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurfaceAlt
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.media.QuataMediaCache
import com.quata.core.media.withQuataMediaMetadataRetriever
import com.quata.documentreader.QuataDocumentPreviewRenderer
import com.quata.documentreader.QuataDocumentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class AttachmentPreview(
    val name: String,
    val uri: String,
    val mimeType: String?
) {
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
    val isVideo: Boolean get() = mimeType?.startsWith("video/") == true
    val isAudio: Boolean get() = mimeType?.startsWith("audio/") == true
    val isMedia: Boolean get() = isImage || isVideo
    val isDocument: Boolean get() = QuataDocumentReader.canOpen(Uri.parse(uri), name, mimeType)
}

@Composable
fun AttachmentThumbnail(
    attachment: AttachmentPreview,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(QuataSurfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        when {
            attachment.isImage -> AsyncImage(
                model = attachment.uri,
                contentDescription = attachment.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            attachment.isVideo -> {
                VideoAttachmentThumbnail(
                    uri = attachment.uri,
                    name = attachment.name,
                    modifier = Modifier.fillMaxSize()
                )
            }

            attachment.isAudio -> CompactIcon(
                Icons.Filled.Mic,
                contentDescription = attachment.name,
                tint = QuataOrange,
                modifier = Modifier.size(32.dp)
            )

            attachment.isDocument -> DocumentAttachmentPreview(
                attachment = attachment,
                iconTint = QuataOrange,
                modifier = Modifier.fillMaxSize()
            )

            else -> CompactIcon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = QuataOrange, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
fun VideoAttachmentThumbnail(
    uri: String,
    name: String,
    modifier: Modifier = Modifier,
    showPlayButton: Boolean = false
) {
    val frame = rememberVideoFrameBitmap(uri)
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (frame != null) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (showPlayButton) {
            Surface(
                color = Color.Black.copy(alpha = 0.38f),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(62.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CompactIcon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioAttachmentPlayer(
    attachment: AttachmentPreview,
    textColor: Color,
    autoPlay: Boolean = false,
    pauseRequested: Boolean = false,
    onPlaybackStarted: () -> Unit = {},
    onPlaybackEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var routeToEarpiece by remember(attachment.uri) { mutableStateOf(false) }
    val audioRouter = remember(context) {
        VoiceNoteAudioRouter(context.applicationContext) { shouldUseEarpiece ->
            routeToEarpiece = shouldUseEarpiece
        }
    }
    val player = remember(attachment.uri) {
        ExoPlayer.Builder(context, VoiceNoteRenderersFactory(context)).build().apply {
            setAudioAttributes(VoiceNoteSpeakerAudioAttributes, true)
            setMediaItem(MediaItem.fromUri(Uri.parse(attachment.uri)))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    var isPlaying by remember(attachment.uri) { mutableStateOf(false) }
    var durationMillis by remember(attachment.uri) { mutableStateOf(0L) }
    var positionMillis by remember(attachment.uri) { mutableStateOf(0L) }
    var hasError by remember(attachment.uri) { mutableStateOf(false) }
    var hasAutoPlayedCurrentAttachment by remember(attachment.uri) { mutableStateOf(false) }
    var scrubberSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = if (durationMillis > 0) {
        (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    fun seekToFraction(fraction: Float) {
        if (durationMillis <= 0L) return
        val target = (durationMillis.toFloat() * fraction.coerceIn(0f, 1f))
            .roundToInt()
            .toLong()
            .coerceIn(0L, durationMillis)
        positionMillis = target
        player.seekTo(target)
    }
    fun seekToX(x: Float) {
        val width = scrubberSize.width.toFloat().coerceAtLeast(1f)
        seekToFraction(x / width)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val playerDuration = player.duration.takeIf { it > 0L }
                if (playerDuration != null) {
                    durationMillis = playerDuration
                }
                if (playbackState == Player.STATE_ENDED) {
                    player.pause()
                    isPlaying = false
                    positionMillis = 0L
                    player.seekTo(0L)
                    audioRouter.stop()
                    onPlaybackEnded()
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
                if (isPlayingNow) {
                    onPlaybackStarted()
                } else {
                    audioRouter.stop()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isPlaying = false
                audioRouter.stop()
            }
        }
        player.addListener(listener)
        onDispose {
            isPlaying = false
            audioRouter.stop()
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(autoPlay, hasAutoPlayedCurrentAttachment, hasError) {
        if (autoPlay && !hasAutoPlayedCurrentAttachment && !hasError) {
            hasAutoPlayedCurrentAttachment = true
            player.seekTo(0L)
            player.play()
        }
        if (!autoPlay && hasAutoPlayedCurrentAttachment && !isPlaying) {
            hasAutoPlayedCurrentAttachment = false
        }
    }

    DisposableEffect(isPlaying, audioRouter) {
        if (isPlaying) {
            audioRouter.start()
        } else {
            audioRouter.stop()
        }
        onDispose {
            audioRouter.stop()
        }
    }

    DisposableEffect(context, isPlaying) {
        val activity = if (isPlaying) context.findActivity() else null
        val previousOrientation = activity?.requestedOrientation
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
        onDispose {
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    LaunchedEffect(player, routeToEarpiece) {
        player.setAudioAttributes(
            if (routeToEarpiece) VoiceNoteEarpieceAudioAttributes else VoiceNoteSpeakerAudioAttributes,
            !routeToEarpiece
        )
    }

    LaunchedEffect(pauseRequested) {
        if (pauseRequested && isPlaying) {
            player.pause()
            isPlaying = false
        }
    }

    LaunchedEffect(player, isPlaying, hasError) {
        while (!hasError && (isPlaying || player.playbackState == Player.STATE_READY)) {
            positionMillis = player.currentPosition.coerceAtLeast(0L)
            player.duration.takeIf { it > 0L }?.let { durationMillis = it }
            delay(250L)
        }
    }

    Surface(
        color = Color.Black.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(QuataOrange),
                contentAlignment = Alignment.Center
            ) {
                CompactIconButton(
                    enabled = !hasError,
                    onClick = {
                        if (isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            if (player.playbackState == Player.STATE_ENDED) {
                                player.seekTo(0L)
                            }
                            player.play()
                        }
                    }
                ) {
                    CompactIcon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.video_editor_play_pause),
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(24) { index ->
                        val barHeight = (8 + ((index * 7) % 18)).dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(3.dp))
                                .background(textColor.copy(alpha = if (index / 24f <= progress) 0.82f else 0.28f))
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .onSizeChanged { scrubberSize = it }
                        .pointerInput(durationMillis, scrubberSize) {
                            detectTapGestures { offset -> seekToX(offset.x) }
                        }
                        .pointerInput(durationMillis, scrubberSize) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> seekToX(offset.x) },
                                onHorizontalDrag = { change, _ -> seekToX(change.position.x) }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = textColor.copy(alpha = 0.78f),
                        trackColor = textColor.copy(alpha = 0.18f)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactIcon(
                        Icons.Filled.Mic,
                        contentDescription = null,
                        tint = textColor.copy(alpha = 0.68f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (hasError) {
                            attachment.name
                        } else {
                            formatAudioAttachmentMillis(if (isPlaying) positionMillis else durationMillis)
                        },
                        color = textColor.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentAttachmentPreview(
    attachment: AttachmentPreview,
    modifier: Modifier = Modifier,
    iconTint: Color = QuataOrange
) {
    val context = LocalContext.current
    var bitmap by remember(attachment.uri, attachment.name, attachment.mimeType) { mutableStateOf<Bitmap?>(null) }
    val uri = remember(attachment.uri) { Uri.parse(attachment.uri) }

    LaunchedEffect(uri, attachment.name, attachment.mimeType) {
        bitmap = withContext(Dispatchers.IO) {
            QuataDocumentPreviewRenderer.renderFirstPage(
                context = context,
                uri = uri,
                fileName = attachment.name,
                mimeType = attachment.mimeType
            )
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(QuataSurfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        val previewBitmap = bitmap
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = attachment.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            CompactIcon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
fun AttachmentViewerDialog(
    attachment: AttachmentPreview,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasOpened = true
        visible = true
    }

    Dialog(
        onDismissRequest = {
            visible = false
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.18f) + fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF05070C))
                    .navigationBarsPadding()
            ) {
                AttachmentViewerTopBar(
                    title = attachment.name,
                    onBack = { visible = false }
                )
                when {
                    attachment.isImage -> ZoomableImage(attachment)
                    attachment.isVideo -> FullscreenVideoPlayer(attachment.uri)
                }
            }
        }
    }

    LaunchedEffect(visible, hasOpened) {
        if (hasOpened && !visible) {
            delay(170L)
            onDismiss()
        }
    }
}

fun Context.openAttachmentWithChooser(attachment: AttachmentPreview) {
    val uri = Uri.parse(attachment.uri)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        startActivity(Intent.createChooser(intent, attachment.name))
    }.onFailure {
        Toast.makeText(this, attachment.name, Toast.LENGTH_SHORT).show()
    }
}

fun Context.openAttachmentWithDocumentReaderOrChooser(
    attachment: AttachmentPreview,
    isDarkMode: Boolean
) {
    val uri = Uri.parse(attachment.uri)
    val openedInternally = runCatching {
        QuataDocumentReader.open(
            context = this,
            uri = uri,
            fileName = attachment.name,
            mimeType = attachment.mimeType,
            isDarkMode = isDarkMode
        )
    }.getOrDefault(false)
    if (!openedInternally) {
        openAttachmentWithChooser(attachment)
    }
}

@Composable
private fun AttachmentViewerTopBar(title: String, onBack: () -> Unit) {
    val template = quataTheme()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .zIndex(2f)
            .background(template.colors.topChrome)
            .padding(horizontal = 8.dp)
    ) {
        CompactIconButton(onClick = onBack) {
            CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = template.colors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            color = template.colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ZoomableImage(attachment: AttachmentPreview) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    offset = if (nextScale == 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        AsyncImage(
            model = attachment.uri,
            contentDescription = attachment.name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun FullscreenVideoPlayer(videoUri: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackRotation by remember(videoUri) { mutableStateOf(0) }
    var isLoading by remember(videoUri) { mutableStateOf(true) }
    LaunchedEffect(videoUri) {
        playbackRotation = withContext(Dispatchers.IO) {
            readQuataVideoRotation(context, Uri.parse(videoUri))
        }
    }
    val player = remember(videoUri) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(QuataMediaCache.videoMediaSourceFactory(context))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(videoUri))
                repeatMode = Player.REPEAT_MODE_OFF
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            }

            override fun onRenderedFirstFrame() {
                isLoading = false
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                player.playWhenReady = false
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { viewContext ->
                (LayoutInflater.from(viewContext)
                    .inflate(R.layout.quata_attachment_player_texture, null, false) as PlayerView).apply {
                    this.player = player
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.useController = true
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                if (playerView.player !== player) {
                    playerView.player = player
                }
                playerView.findQuataTextureView()
                    ?.applyQuataVideoPlaybackTransform(playbackRotation)
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isLoading) {
            Surface(
                color = Color.Black.copy(alpha = 0.36f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(58.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = QuataOrange,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberVideoFrameBitmap(uri: String): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                QuataMediaCache.cachedVideoThumbnail(context, uri)?.let { return@runCatching it }
                val parsedUri = Uri.parse(uri)
                withQuataMediaMetadataRetriever { retriever ->
                    retriever.setAttachmentVideoSource(context, parsedUri)
                    val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()
                        ?: 0
                    val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()
                        ?: 0
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull()
                        ?.normalizedQuataVideoRotation()
                        ?: 0
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.orientAttachmentVideoFrameIfNeeded(rawWidth, rawHeight, rotation)
                        ?.scaledForAttachmentThumbnail()
                        ?.let { frame -> QuataMediaCache.cacheVideoThumbnail(context, uri, frame) }
                }
            }.getOrNull()
        }
    }
    return bitmap
}

private fun MediaMetadataRetriever.setAttachmentVideoSource(context: Context, uri: Uri) {
    when (uri.scheme?.lowercase()) {
        null, "", "content", "file", "android.resource" -> setDataSource(context, uri)
        "http", "https" -> setDataSource(uri.toString(), emptyMap())
        else -> setDataSource(context, uri)
    }
}

private fun Bitmap.orientAttachmentVideoFrameIfNeeded(
    rawWidth: Int,
    rawHeight: Int,
    rotationDegrees: Int
): Bitmap {
    val rotation = rotationDegrees.normalizedQuataVideoRotation()
    if (rotation == 0) return this
    if (rawWidth <= 0 || rawHeight <= 0 || width <= 0 || height <= 0) return this
    if (rotation == 90 || rotation == 270) {
        val expectedPortrait = rawHeight < rawWidth
        val bitmapPortrait = height > width
        if (expectedPortrait == bitmapPortrait) return this
    }
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (rotated !== this) recycle()
    return rotated
}

private fun Bitmap.scaledForAttachmentThumbnail(maxDimension: Int = 512): Bitmap {
    val largest = maxOf(width, height)
    if (largest <= maxDimension || largest <= 0) return this
    val scale = maxDimension.toFloat() / largest.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}

private fun formatAudioAttachmentMillis(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private val VoiceNoteSpeakerAudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
    .build()

private val VoiceNoteEarpieceAudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_VOICE_COMMUNICATION)
    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
    .build()

private class VoiceNoteRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(voiceNoteChannelMixer()))
            .build()
    }

    private fun voiceNoteChannelMixer(): ChannelMixingAudioProcessor {
        return ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2))
            for (channelCount in 2..8) {
                putChannelMixingMatrix(ChannelMixingMatrix.create(channelCount, channelCount))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class VoiceNoteAudioRouter(
    context: Context,
    private val onRouteChanged: (Boolean) -> Unit
) : SensorEventListener {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private var isListening = false
    private var isRoutedToEarpiece = false
    private var previousAudioMode: Int? = null
    private var previousLegacySpeakerphoneOn: Boolean? = null

    fun start() {
        val sensor = proximitySensor ?: return
        if (isListening) return
        isListening = true
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (isListening) {
            sensorManager?.unregisterListener(this)
            isListening = false
        }
        restoreSpeakerRoute()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val sensor = proximitySensor ?: return
        val distance = event.values.firstOrNull() ?: return
        val isNear = distance < sensor.maximumRange.coerceAtMost(PROXIMITY_NEAR_THRESHOLD_CM)
        if (isNear) {
            routeToEarpiece()
        } else {
            restoreSpeakerRoute()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun routeToEarpiece() {
        val manager = audioManager ?: return
        if (isRoutedToEarpiece) return
        previousAudioMode = manager.mode
        var didRoute = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val earpiece = manager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            if (earpiece != null) {
                manager.mode = AudioManager.MODE_IN_COMMUNICATION
                didRoute = manager.setCommunicationDevice(earpiece)
            }
        } else {
            previousLegacySpeakerphoneOn = manager.legacySpeakerphoneOn()
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            manager.setLegacySpeakerphoneOn(false)
            didRoute = true
        }
        if (!didRoute) {
            previousAudioMode = null
            previousLegacySpeakerphoneOn = null
            return
        }
        isRoutedToEarpiece = true
        onRouteChanged(true)
    }

    private fun restoreSpeakerRoute() {
        val manager = audioManager ?: return
        if (!isRoutedToEarpiece) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.clearCommunicationDevice()
        } else {
            previousLegacySpeakerphoneOn?.let { manager.setLegacySpeakerphoneOn(it) }
        }
        previousAudioMode?.let { manager.mode = it }
        previousAudioMode = null
        previousLegacySpeakerphoneOn = null
        isRoutedToEarpiece = false
        onRouteChanged(false)
    }

    // Android 11 and older do not expose setCommunicationDevice(), so earpiece routing needs this fallback.
    @Suppress("DEPRECATION")
    private fun AudioManager.legacySpeakerphoneOn(): Boolean = isSpeakerphoneOn

    @Suppress("DEPRECATION")
    private fun AudioManager.setLegacySpeakerphoneOn(enabled: Boolean) {
        isSpeakerphoneOn = enabled
    }

    private companion object {
        const val PROXIMITY_NEAR_THRESHOLD_CM = 5f
    }
}
