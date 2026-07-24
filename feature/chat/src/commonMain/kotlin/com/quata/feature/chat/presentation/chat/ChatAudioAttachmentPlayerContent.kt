package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

/**
 * Portable audio-attachment controls. The host owns Media3/AVFoundation state and supplies
 * playback commands, so URI access, routing and player lifecycle stay platform-specific.
 */
@Composable
fun ChatAudioAttachmentPlayerContent(
    isPlaying: Boolean,
    hasError: Boolean,
    progress: Float,
    displayText: String,
    textColor: Color,
    playPauseDescription: String,
    onTogglePlayback: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubberSize by remember { mutableStateOf(IntSize.Zero) }
    fun seekToX(x: Float) {
        val width = scrubberSize.width.toFloat().coerceAtLeast(1f)
        onSeekToFraction(x / width)
    }

    Surface(
        color = Color.Black.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(QuataOrange),
                contentAlignment = Alignment.Center,
            ) {
                CompactIconButton(enabled = !hasError, onClick = onTogglePlayback) {
                    CompactIcon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = playPauseDescription,
                        tint = Color.White,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(26.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(24) { index ->
                        val barHeight = (8 + ((index * 7) % 18)).dp
                        Box(
                            modifier = Modifier.width(3.dp).height(barHeight).clip(RoundedCornerShape(3.dp))
                                .background(textColor.copy(alpha = if (index / 24f <= progress) 0.82f else 0.28f)),
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(14.dp).onSizeChanged { scrubberSize = it }
                        .pointerInput(scrubberSize) { detectTapGestures { offset -> seekToX(offset.x) } }
                        .pointerInput(scrubberSize) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset -> seekToX(offset.x) },
                                onHorizontalDrag = { change, _ -> seekToX(change.position.x) },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp)),
                        color = textColor.copy(alpha = 0.78f),
                        trackColor = textColor.copy(alpha = 0.18f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactIcon(Icons.Filled.Mic, contentDescription = null, tint = textColor.copy(alpha = 0.68f), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(displayText, color = textColor.copy(alpha = 0.68f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
