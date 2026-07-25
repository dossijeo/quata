package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
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
        ReelVideoControlsContent(
            state = ReelVideoControlsState(
                isPlaying = state.isPlaying,
                isBuffering = state.isRebuffering,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isMuted = state.isMuted,
                showMuteButton = state.showMuteButton,
            ),
            strings = ReelVideoControlsStrings(
                play = strings.play,
                pause = strings.pause,
                mute = strings.mute,
                unmute = strings.unmute,
            ),
            playPauseIcon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            muteIcon = if (state.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            onPlayPause = { toggle(showFeedback = false) },
            onProgressChange = { progress ->
                onSeek((progress * state.durationMs.coerceAtLeast(1L).toFloat()).toLong())
            },
            onToggleMute = onToggleMute,
            timeline = { progress, onProgressChange ->
                ReelTimelineThumbContent(
                    progress = progress,
                    onProgressChange = onProgressChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp),
                )
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 96.dp, bottom = 8.dp),
        )
    }
}
