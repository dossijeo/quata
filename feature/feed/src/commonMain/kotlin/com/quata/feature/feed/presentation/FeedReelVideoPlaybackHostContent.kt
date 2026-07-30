package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

/** Portable state published by a reel playback engine to the shared visual host. */
data class VideoPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isMuted: Boolean = false,
    val showMuteButton: Boolean = true,
    val hasStartedPlayback: Boolean = false,
    val isEnded: Boolean = false,
    val error: String? = null,
    val feedback: VideoPlaybackFeedback? = null,
) {
    val isRebuffering: Boolean get() = isBuffering && hasStartedPlayback
}

/** The two transient feedback states that are portable across visual media hosts. */
enum class VideoPlaybackFeedback { Play, Pause }

/** Localized labels for the shared reel video controls. */
data class VideoPlaybackStrings(
    val play: String,
    val pause: String,
    val mute: String,
    val unmute: String,
)

/**
 * Shared reel playback chrome around a platform-owned renderer.
 *
 * The [media] slot retains URI loading, AV/Media3/browser elements and lifecycle policy in the
 * platform host. This common layer owns only gesture routing, portable state presentation and
 * controls. A completed or failed stream deliberately routes to dedicated callbacks instead of
 * assuming that every platform uses the Android feed's looping policy.
 */
@Composable
fun FeedReelVideoPlaybackHostContent(
    state: VideoPlaybackState,
    strings: VideoPlaybackStrings,
    media: @Composable BoxScope.() -> Unit,
    onPlay: (showFeedback: Boolean) -> Unit,
    onPause: (showFeedback: Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun toggle(showFeedback: Boolean) = when {
        state.error != null -> onError()
        state.isEnded -> onEnded()
        state.isPlaying -> onPause(showFeedback)
        else -> onPlay(showFeedback)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Transparent),
    ) {
        media()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { toggle(showFeedback = true) },
        )
        ReelPlaybackFeedbackContent(
            feedbackIcon = when (state.feedback) {
                VideoPlaybackFeedback.Play -> Icons.Filled.PlayArrow
                VideoPlaybackFeedback.Pause -> Icons.Filled.Pause
                null -> null
            },
            isRebuffering = state.isRebuffering,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 96.dp, bottom = 8.dp)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (state.isPlaying) strings.pause else strings.play,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.clickable { toggle(showFeedback = false) },
            )
            ReelTimelineThumbContent(
                progress = (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1L).toFloat())
                    .coerceIn(0f, 1f),
                onProgressChange = { progress ->
                    onSeek((progress * state.durationMs.coerceAtLeast(1L).toFloat()).toLong())
                },
                modifier = Modifier
                    .width(120.dp)
                    .height(30.dp),
            )
            Text(
                text = formatFeedReelPlaybackTime(state.positionMs, state.durationMs),
                color = Color.White,
                fontSize = 12.sp,
            )
            if (state.showMuteButton) {
                Text(
                    text = if (state.isMuted) strings.unmute else strings.mute,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onToggleMute),
                )
            }
        }
    }
}

private fun formatFeedReelPlaybackTime(positionMs: Long, durationMs: Long): String =
    "${formatFeedReelPlaybackTimePart(positionMs)} / ${formatFeedReelPlaybackTimePart(durationMs)}"

private fun formatFeedReelPlaybackTimePart(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
