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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

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
        FeedReelPlaybackFeedbackIconContent(
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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactIconButton(onClick = { toggle(showFeedback = false) }) {
                CompactIcon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) strings.pause else strings.play,
                    tint = Color.White,
                )
            }
            ReelTimelineThumbContent(
                progress = (state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1L).toFloat())
                    .coerceIn(0f, 1f),
                onProgressChange = { progress ->
                    onSeek((progress * state.durationMs.coerceAtLeast(1L).toFloat()).toLong())
                },
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp),
            )
            Text(
                text = formatFeedReelPlaybackTime(state.positionMs, state.durationMs),
                color = Color.White,
                fontSize = 12.sp,
            )
            if (state.showMuteButton) {
                CompactIconButton(onClick = onToggleMute) {
                    CompactIcon(
                        imageVector = if (state.isMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (state.isMuted) strings.unmute else strings.mute,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Keeps the Android reel-feedback silhouette while avoiding font-dependent control glyphs.
 *
 * The old implementation used a 54sp glyph inside this rounded rectangular surface. The
 * vector is deliberately 54dp and retains the original horizontal/vertical padding, alpha and
 * corner radius so it does not inherit the circular geometry used by other reel feedback hosts.
 */
@Composable
private fun FeedReelPlaybackFeedbackIconContent(
    feedbackIcon: ImageVector?,
    isRebuffering: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (feedbackIcon != null) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(46.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = feedbackIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp),
                )
            }
        } else if (isRebuffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

private fun formatFeedReelPlaybackTime(positionMs: Long, durationMs: Long): String =
    "${formatFeedReelPlaybackTimePart(positionMs)} / ${formatFeedReelPlaybackTimePart(durationMs)}"

private fun formatFeedReelPlaybackTimePart(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
