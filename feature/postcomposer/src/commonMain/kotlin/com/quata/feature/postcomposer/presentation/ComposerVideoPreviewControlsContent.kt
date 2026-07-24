package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

/**
 * Shared playback-control rail for media previews.
 *
 * Playback engines, URI loading and lifecycle handling are platform responsibilities. This
 * component owns only the portable control geometry and maps user intent to callbacks supplied
 * by the host.
 */
@Composable
fun ComposerVideoPreviewControlsContent(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    playContentDescription: String,
    pauseContentDescription: String,
    replayContentDescription: String,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(18.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactIconButton(onClick = onPlayPause, modifier = Modifier.size(34.dp)) {
            CompactIcon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) pauseContentDescription else playContentDescription,
                tint = Color.White,
            )
        }
        CompactIconButton(onClick = onReplay, modifier = Modifier.size(34.dp)) {
            CompactIcon(
                imageVector = Icons.Filled.Replay,
                contentDescription = replayContentDescription,
                tint = Color.White,
            )
        }
        Slider(
            value = progress,
            onValueChange = { onSeek((it * duration).toLong()) },
            enabled = durationMs > 0,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
        )
        Text(
            text = "${formatComposerPreviewVideoTime(positionMs)} / ${formatComposerPreviewVideoTime(durationMs)}",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(70.dp),
            maxLines = 1,
        )
    }
}

private fun formatComposerPreviewVideoTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
