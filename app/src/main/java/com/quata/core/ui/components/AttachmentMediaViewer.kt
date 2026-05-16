package com.quata.core.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurfaceAlt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AttachmentPreview(
    val name: String,
    val uri: String,
    val mimeType: String?
) {
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true
    val isVideo: Boolean get() = mimeType?.startsWith("video/") == true
    val isMedia: Boolean get() = isImage || isVideo
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
                val frame = rememberVideoFrameBitmap(attachment.uri)
                if (frame != null) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = attachment.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            else -> Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = QuataOrange, modifier = Modifier.size(30.dp))
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

@Composable
private fun AttachmentViewerTopBar(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(Color(0xFF0D1422))
            .padding(horizontal = 8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            title,
            color = Color.White,
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
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    offset = if (nextScale == 1f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center
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
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun FullscreenVideoPlayer(videoUri: String) {
    val context = LocalContext.current
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}

@Composable
private fun rememberVideoFrameBitmap(uri: String): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    val parsedUri = Uri.parse(uri)
                    if (parsedUri.scheme == "content" || parsedUri.scheme == "file") {
                        retriever.setDataSource(context, parsedUri)
                    } else {
                        retriever.setDataSource(uri, emptyMap())
                    }
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull()
        }
    }
    return bitmap
}
