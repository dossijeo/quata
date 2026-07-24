package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

/** Platform-neutral state needed to render a reel playback-control strip. */
data class ReelVideoControlsState(
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val isMuted: Boolean,
    val showMuteButton: Boolean,
) {
    val progress: Float
        get() = (positionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    val durationText: String
        get() = "${formatReelVideoTime(positionMs)} / ${formatReelVideoTime(durationMs)}"
}

/** Host-localized accessibility labels for the portable controls. */
data class ReelVideoControlsStrings(
    val play: String,
    val pause: String,
    val mute: String,
    val unmute: String,
)

/**
 * Portable playback-control appearance and state wiring.
 *
 * Player state, seek behavior and media resources remain host-owned. Hosts inject only their
 * scrubber implementation and icon vectors, allowing Android, Web and iOS to share the visual
 * state transitions without importing Media3 or platform URI APIs into commonMain.
 */
@Composable
fun ReelVideoControlsContent(
    state: ReelVideoControlsState,
    strings: ReelVideoControlsStrings,
    playPauseIcon: ImageVector,
    muteIcon: ImageVector,
    onPlayPause: () -> Unit,
    onProgressChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    timeline: @Composable RowScope.(progress: Float, onProgressChange: (Float) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompactIconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp)) {
            if (state.isBuffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                CompactIcon(
                    imageVector = playPauseIcon,
                    contentDescription = if (state.isPlaying) strings.pause else strings.play,
                    tint = Color.White,
                )
            }
        }
        timeline(state.progress, onProgressChange)
        Text(
            text = state.durationText,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.width(82.dp),
        )
        if (state.showMuteButton) {
            CompactIconButton(onClick = onToggleMute, modifier = Modifier.size(38.dp)) {
                CompactIcon(
                    imageVector = muteIcon,
                    contentDescription = if (state.isMuted) strings.unmute else strings.mute,
                    tint = Color.White,
                )
            }
        }
    }
}

private fun formatReelVideoTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
