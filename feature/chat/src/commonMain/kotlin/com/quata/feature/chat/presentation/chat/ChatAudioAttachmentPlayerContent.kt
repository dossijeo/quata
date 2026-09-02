package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

const val ChatAudioAttachmentPlayerTestTag = "chat.attachment.audio.player"
const val ChatAudioAttachmentToggleTestTag = "chat.attachment.audio.toggle"
const val ChatAudioAttachmentProgressTestTag = "chat.attachment.audio.progress"
const val ChatAudioAttachmentStateLoading = "chat.attachment.audio.state.loading"
const val ChatAudioAttachmentStatePlaying = "chat.attachment.audio.state.playing"
const val ChatAudioAttachmentStatePaused = "chat.attachment.audio.state.paused"
const val ChatAudioAttachmentStateFailed = "chat.attachment.audio.state.failed"

/**
 * Portable audio-attachment controls. The host owns Media3/AVFoundation state and supplies
 * playback commands, so URI access, routing and player lifecycle stay platform-specific.
 */
@Composable
fun ChatAudioAttachmentPlayerContent(
    isPlaying: Boolean,
    hasError: Boolean,
    isLoading: Boolean = false,
    progress: Float,
    displayText: String,
    errorText: String,
    textColor: Color,
    playPauseDescription: String,
    onTogglePlayback: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubberSize by remember { mutableStateOf(IntSize.Zero) }
    val boundedProgress = progress.coerceIn(0f, 1f)
    val progressPercent = (boundedProgress * 100f).toInt().coerceIn(0, 100)
    val toggleDescription = if (isLoading) "Loading $displayText" else "$playPauseDescription $displayText"
    val playbackStateDescription = when {
        hasError -> ChatAudioAttachmentStateFailed
        isLoading -> ChatAudioAttachmentStateLoading
        isPlaying -> ChatAudioAttachmentStatePlaying
        else -> ChatAudioAttachmentStatePaused
    }
    fun seekToFraction(fraction: Float) {
        onSeekToFraction(fraction.coerceIn(0f, 1f))
    }
    fun seekToX(x: Float) {
        val width = scrubberSize.width.toFloat().coerceAtLeast(1f)
        seekToFraction(x / width)
    }

    Surface(
        color = Color.Black.copy(alpha = 0.12f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().semantics {
            testTag = ChatAudioAttachmentPlayerTestTag
            stateDescription = playbackStateDescription
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(QuataOrange),
                contentAlignment = Alignment.Center,
            ) {
                CompactIconButton(
                    enabled = !hasError,
                    onClick = onTogglePlayback,
                    modifier = Modifier.semantics {
                        testTag = ChatAudioAttachmentToggleTestTag
                        contentDescription = toggleDescription
                        stateDescription = playbackStateDescription
                    },
                ) {
                    CompactIcon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
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
                        progress = { boundedProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(999.dp))
                            .semantics {
                                testTag = ChatAudioAttachmentProgressTestTag
                                contentDescription = "$ChatAudioAttachmentProgressTestTag $displayText $progressPercent%"
                                progressBarRangeInfo = ProgressBarRangeInfo(boundedProgress, 0f..1f, 0)
                                setProgress { target ->
                                    seekToFraction(target)
                                    true
                                }
                            }
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> seekToFraction(boundedProgress - 0.1f)
                                    Key.DirectionRight -> seekToFraction(boundedProgress + 0.1f)
                                    Key.PageDown -> seekToFraction(boundedProgress - 0.25f)
                                    Key.PageUp -> seekToFraction(boundedProgress + 0.25f)
                                    Key.MoveHome -> seekToFraction(0f)
                                    Key.MoveEnd -> seekToFraction(1f)
                                    else -> return@onKeyEvent false
                                }
                                true
                            },
                        color = textColor.copy(alpha = 0.78f),
                        trackColor = textColor.copy(alpha = 0.18f),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactIcon(Icons.Filled.Mic, contentDescription = null, tint = textColor.copy(alpha = 0.68f), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (hasError) errorText else displayText,
                        color = if (hasError) QuataOrange else textColor.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
