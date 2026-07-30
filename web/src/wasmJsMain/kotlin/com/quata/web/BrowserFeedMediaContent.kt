@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.WebElementView
import com.quata.core.model.Post
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.feed.presentation.FeedReelVideoPlaybackHostContent
import com.quata.feature.feed.presentation.ReelMediaSurfaceContent
import com.quata.feature.feed.presentation.VideoPlaybackFeedback
import com.quata.feature.feed.presentation.VideoPlaybackState
import com.quata.feature.feed.presentation.VideoPlaybackStrings
import kotlinx.browser.document
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLVideoElement

/**
 * Wasm renderer for the common reel playback host.
 *
 * The DOM element is deliberately only the decoding surface: controls, gestures, feedback and
 * timeline are exactly the Compose [FeedReelVideoPlaybackHostContent] used by Android.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BrowserFeedMediaContent(
    post: Post,
    isCurrent: Boolean,
    isMuted: Boolean,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    onMuteChange: (Boolean) -> Unit,
) {
    // URLs can be signed CDN paths without an extension; the model itself identifies media type.
    val videoUrl = post.videoUrl?.takeIf(::isBrowserFeedMediaUrl)
    val imageUrl = post.imageUrl?.takeIf(::isBrowserFeedMediaUrl)
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape
    when {
        videoUrl != null -> BrowserFeedVideoContent(
            videoUrl = videoUrl,
            isCurrent = isCurrent,
            isMuted = isMuted,
            isLandscape = isLandscape,
            initialPositionMs = initialPositionMs,
            onPositionChanged = onPositionChanged,
            onMuteChange = onMuteChange,
        )
        imageUrl != null -> ReelMediaSurfaceContent(background = textCanvasBrush(imageUrl)) {
            BrowserCanvasImage(
                url = imageUrl,
                contentDescription = post.text.take(120).takeIf { it.isNotBlank() },
                contentScale = browserFeedImageContentScale(isLandscape),
                modifier = Modifier.fillMaxSize(),
            )
        }
        else -> Unit
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BrowserFeedVideoContent(
    videoUrl: String,
    isCurrent: Boolean,
    isMuted: Boolean,
    isLandscape: Boolean,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    onMuteChange: (Boolean) -> Unit,
) {
    val latestPositionChanged by rememberUpdatedState(onPositionChanged)
    val latestIsCurrent = rememberUpdatedState(isCurrent)
    val released = remember(videoUrl) { mutableStateOf(false) }
    var element by remember(videoUrl) { mutableStateOf<HTMLVideoElement?>(null) }
    var isPlaying by remember(videoUrl) { mutableStateOf(false) }
    var isBuffering by remember(videoUrl) { mutableStateOf(false) }
    var hasStartedPlayback by remember(videoUrl) { mutableStateOf(initialPositionMs > 0L) }
    var isEnded by remember(videoUrl) { mutableStateOf(false) }
    var playbackError by remember(videoUrl) { mutableStateOf<String?>(null) }
    var positionMs by remember(videoUrl) { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember(videoUrl) { mutableLongStateOf(0L) }
    var feedback by remember(videoUrl) { mutableStateOf<VideoPlaybackFeedback?>(null) }
    var feedbackTick by remember(videoUrl) { mutableLongStateOf(0L) }
    var appliedInitialPosition by remember(videoUrl) { mutableStateOf(false) }

    fun persistPosition(milliseconds: Long) {
        positionMs = milliseconds.coerceAtLeast(0L)
        latestPositionChanged(positionMs)
    }

    fun showFeedback(value: VideoPlaybackFeedback) {
        feedback = value
        feedbackTick = currentBrowserTimeMillis()
    }

    fun play(showFeedback: Boolean) {
        val video = element ?: return
        playbackError = null
        isEnded = false
        video.muted = isMuted
        requestBrowserVideoPlay(video, isReleased = { released.value }) { rejection ->
            if (released.value) return@requestBrowserVideoPlay
            isPlaying = false
            isBuffering = false
            if (!isMuted && isBrowserAutoplayPolicyRejection(rejection)) {
                // Browsers forbid an initial audible play. Persist the same mute choice for all
                // reels and let the host effect replay immediately with audio disabled.
                onMuteChange(true)
            } else {
                playbackError = "feed_video_playback_failed"
            }
        }
        if (showFeedback) showFeedback(VideoPlaybackFeedback.Play)
    }

    fun pause(showFeedback: Boolean) {
        element?.pause()
        isPlaying = false
        isBuffering = false
        if (showFeedback) showFeedback(VideoPlaybackFeedback.Pause)
    }

    LaunchedEffect(element, isCurrent, isMuted) {
        val video = element ?: return@LaunchedEffect
        video.muted = isMuted
        if (isCurrent) play(showFeedback = false) else pause(showFeedback = false)
    }

    LaunchedEffect(feedbackTick) {
        val tick = feedbackTick
        if (tick != 0L) {
            delay(650)
            if (feedbackTick == tick) feedback = null
        }
    }

    DisposableEffect(element) {
        onDispose {
            element?.let { video ->
                persistPosition((video.currentTime * 1_000.0).toLong())
                video.pause()
                clearBrowserFeedVideoListeners(video)
            }
        }
    }

    FeedReelVideoPlaybackHostContent(
        state = VideoPlaybackState(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            positionMs = positionMs,
            durationMs = durationMs,
            isMuted = isMuted,
            showMuteButton = !isLandscape,
            hasStartedPlayback = hasStartedPlayback,
            isEnded = isEnded,
            error = playbackError,
            feedback = feedback,
        ),
        strings = VideoPlaybackStrings(
            play = "Reproducir",
            pause = "Pausar",
            mute = "Silenciar",
            unmute = "Activar sonido",
        ),
        media = {
            WebElementView(
                factory = {
                    (document.createElement("video") as HTMLVideoElement).apply {
                        controls = false
                        preload = "metadata"
                        loop = true
                        muted = isMuted
                        setAttribute("playsinline", "")
                        style.width = "100%"
                        style.height = "100%"
                        released.value = false
                        installBrowserFeedVideoListeners(
                            video = this,
                            isReleased = { released.value },
                            onMetadata = { duration ->
                                durationMs = (duration * 1_000.0).toLong().coerceAtLeast(0L)
                                if (!appliedInitialPosition && initialPositionMs > 0L) {
                                    currentTime = initialPositionMs / 1_000.0
                                    persistPosition(initialPositionMs)
                                }
                                appliedInitialPosition = true
                            },
                            onProgress = { position, duration ->
                                durationMs = (duration * 1_000.0).toLong().coerceAtLeast(0L)
                                persistPosition((position * 1_000.0).toLong())
                                if (position > 0.0) hasStartedPlayback = true
                            },
                            onPlay = {
                                hasStartedPlayback = true
                                isPlaying = true
                                isBuffering = false
                                playbackError = null
                            },
                            onPause = { isPlaying = false },
                            onWaiting = { if (latestIsCurrent.value) isBuffering = true },
                            onCanPlay = { isBuffering = false },
                            onEnded = { isEnded = true; isPlaying = false },
                            onError = {
                                isPlaying = false
                                isBuffering = false
                                playbackError = "feed_video_playback_failed"
                            },
                        )
                    }.also { element = it }
                },
                onRelease = { video ->
                    released.value = true
                    persistPosition((video.currentTime * 1_000.0).toLong())
                    video.pause()
                    clearBrowserFeedVideoListeners(video)
                    if (element === video) element = null
                },
                update = { video ->
                    if (video.src != videoUrl) video.src = videoUrl
                    video.controls = false
                    video.loop = true
                    video.muted = isMuted
                    video.style.objectFit = if (isLandscape) "contain" else "cover"
                    if (!appliedInitialPosition && initialPositionMs > 0L && video.readyState > 0) {
                        video.currentTime = initialPositionMs / 1_000.0
                        persistPosition(initialPositionMs)
                        appliedInitialPosition = true
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
        onPlay = { showFeedback -> play(showFeedback) },
        onPause = { showFeedback -> pause(showFeedback) },
        onSeek = { targetMs ->
            element?.currentTime = targetMs.coerceAtLeast(0L) / 1_000.0
            persistPosition(targetMs)
        },
        onEnded = {
            element?.currentTime = 0.0
            isEnded = false
            play(showFeedback = false)
        },
        onError = {
            element?.let { video ->
                playbackError = null
                video.load()
                play(showFeedback = false)
            }
        },
        onToggleMute = { onMuteChange(!isMuted) },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun currentBrowserTimeMillis(): Long = js("Date.now()")

private fun requestBrowserVideoPlay(
    video: HTMLVideoElement,
    isReleased: () -> Boolean,
    onRejected: (String) -> Unit,
): Unit = js(
    """{
    const result = video.play?.();
    if (result?.catch) result.catch((error) => {
      if (!isReleased()) onRejected(String(error?.name || error?.message || ''));
    });
    }""",
)

private fun installBrowserFeedVideoListeners(
    video: HTMLVideoElement,
    isReleased: () -> Boolean,
    onMetadata: (Double) -> Unit,
    onProgress: (Double, Double) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onWaiting: () -> Unit,
    onCanPlay: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
): Unit = js(
    """{
    video.onloadedmetadata = () => { if (!isReleased()) onMetadata(Number.isFinite(video.duration) ? video.duration : 0); };
    video.ontimeupdate = () => { if (!isReleased()) onProgress(Number.isFinite(video.currentTime) ? video.currentTime : 0, Number.isFinite(video.duration) ? video.duration : 0); };
    video.onplay = () => { if (!isReleased()) onPlay(); };
    video.onpause = () => { if (!isReleased()) onPause(); };
    video.onwaiting = () => { if (!isReleased()) onWaiting(); };
    video.oncanplay = () => { if (!isReleased()) onCanPlay(); };
    video.onended = () => { if (!isReleased()) onEnded(); };
    video.onerror = () => { if (!isReleased()) onError(); };
    }""",
)

private fun clearBrowserFeedVideoListeners(video: HTMLVideoElement): Unit = js(
    """{
    video.onloadedmetadata = null;
    video.ontimeupdate = null;
    video.onplay = null;
    video.onpause = null;
    video.onwaiting = null;
    video.oncanplay = null;
    video.onended = null;
    video.onerror = null;
    }""",
)

internal fun isBrowserFeedMediaUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

internal fun isBrowserAutoplayPolicyRejection(rejection: String): Boolean =
    rejection.contains("NotAllowedError", ignoreCase = true) ||
        rejection.contains("autoplay", ignoreCase = true)

/** Reels crop in portrait and preserve the complete frame in landscape. */
internal fun browserFeedImageContentScale(isLandscape: Boolean): ContentScale =
    if (isLandscape) ContentScale.Fit else ContentScale.Crop
