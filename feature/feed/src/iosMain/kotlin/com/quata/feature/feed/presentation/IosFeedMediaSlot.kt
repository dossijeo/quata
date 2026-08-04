package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.model.Post
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.textCanvasPattern
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import kotlinx.coroutines.delay
import platform.UIKit.UIView

/**
 * Native media driver supplied by the Swift application target.
 *
 * Xcode 26's AVFoundation declarations are incomplete in the Kotlin/Native platform stubs used
 * by this build, so the one unavoidable AVFoundation boundary lives in Swift. The Feed product
 * surface, playback controls, state and variant selection remain Kotlin/Compose common code.
 */
interface IosFeedMediaFactory {
    fun createImage(url: String): IosFeedMediaSurface
    fun createVideo(url: String): IosFeedMediaSurface
}

/** UIKit/AVFoundation driver contract; it contains no Feed state or Compose controls. */
interface IosFeedMediaSurface {
    fun nativeView(): UIView
    fun configureBackground(startArgb: Int, endArgb: Int)
    fun configure(isActive: Boolean, isMuted: Boolean, initialPositionMs: Long)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun retry()
    fun snapshot(): IosFeedMediaSnapshot
    fun dispose()
}

/** Snapshot polled by Compose so Swift does not own product state or Compose callbacks. */
data class IosFeedMediaSnapshot(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasStartedPlayback: Boolean = false,
    val isEnded: Boolean = false,
    val error: String? = null,
)

@Composable
fun BoxScope.IosFeedMediaSlot(
    post: Post,
    isCurrent: Boolean,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    isMuted: Boolean,
    onMuteChange: (Boolean) -> Unit,
    mediaFactory: IosFeedMediaFactory,
) {
    val videoUrl = post.videoUrl?.trim().orEmpty()
    val imageUrl = post.imageUrl?.trim().orEmpty()
    val backgroundSeed = iosFeedMediaBackgroundSeed(videoUrl, imageUrl)
    val backgroundPattern = remember(backgroundSeed) { textCanvasPattern(backgroundSeed) }
    val surface = remember(post.id, videoUrl, imageUrl) {
        if (videoUrl.isNotEmpty()) mediaFactory.createVideo(videoUrl) else mediaFactory.createImage(imageUrl)
    }
    DisposableEffect(surface) { onDispose(surface::dispose) }
    SideEffect {
        surface.configureBackground(
            startArgb = backgroundPattern.start.toArgb(),
            endArgb = backgroundPattern.end.toArgb(),
        )
    }
    ReelMediaSurfaceContent(background = textCanvasBrush(backgroundSeed)) {
        if (videoUrl.isEmpty()) {
            UIKitView(
                factory = surface::nativeView,
                update = { surface.configure(isActive = false, isMuted = true, initialPositionMs = 0L) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            IosFeedVideoPlayback(
                surface = surface,
                isCurrent = isCurrent,
                initialPositionMs = initialPositionMs,
                isMuted = isMuted,
                onMuteChange = onMuteChange,
                onPositionChanged = onPositionChanged,
            )
        }
    }
}

/** Keeps the iOS native renderer on the same URL-derived visual surface as Android and Wasm. */
internal fun iosFeedMediaBackgroundSeed(videoUrl: String, imageUrl: String): String =
    videoUrl.ifEmpty { imageUrl }

@Composable
private fun IosFeedVideoPlayback(
    surface: IosFeedMediaSurface,
    isCurrent: Boolean,
    initialPositionMs: Long,
    isMuted: Boolean,
    onMuteChange: (Boolean) -> Unit,
    onPositionChanged: (Long) -> Unit,
) {
    var playback by remember(surface) {
        mutableStateOf(IosFeedMediaSnapshot(positionMs = initialPositionMs, hasStartedPlayback = initialPositionMs > 0L))
    }
    var feedback by remember(surface) { mutableStateOf<VideoPlaybackFeedback?>(null) }
    var feedbackTick by remember(surface) { mutableStateOf(0L) }
    val landscape = rememberQuataWindowLayoutInfo().isLandscape

    LaunchedEffect(surface, isCurrent, isMuted, initialPositionMs) {
        surface.configure(isCurrent, isMuted, initialPositionMs)
    }
    LaunchedEffect(surface, isCurrent) {
        while (isCurrent) {
            val latest = surface.snapshot()
            playback = latest
            onPositionChanged(latest.positionMs)
            delay(500)
        }
    }
    LaunchedEffect(feedbackTick) {
        if (feedbackTick != 0L) {
            delay(650)
            feedback = null
        }
    }

    FeedReelVideoPlaybackHostContent(
        state = VideoPlaybackState(
            isPlaying = playback.isPlaying,
            isBuffering = playback.isBuffering,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            isMuted = isMuted,
            showMuteButton = !landscape,
            hasStartedPlayback = playback.hasStartedPlayback,
            isEnded = playback.isEnded,
            error = playback.error,
            feedback = feedback,
        ),
        strings = VideoPlaybackStrings("Reproducir", "Pausar", "Silenciar", "Activar sonido"),
        media = {
            UIKitView(factory = surface::nativeView, update = { }, modifier = Modifier.fillMaxSize())
        },
        onPlay = { showFeedback ->
            surface.play()
            if (showFeedback) { feedback = VideoPlaybackFeedback.Play; feedbackTick += 1L }
        },
        onPause = { showFeedback ->
            surface.pause()
            if (showFeedback) { feedback = VideoPlaybackFeedback.Pause; feedbackTick += 1L }
        },
        onSeek = surface::seekTo,
        onEnded = { surface.seekTo(0L); surface.play() },
        onError = surface::retry,
        onToggleMute = { onMuteChange(toggledFeedMutedState(isMuted)) },
        modifier = Modifier.fillMaxSize(),
    )
}
