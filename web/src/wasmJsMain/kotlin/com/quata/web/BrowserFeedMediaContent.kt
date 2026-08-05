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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.quata.core.config.QuataPublicBackendConfig
import com.quata.core.model.Post
import com.quata.core.ui.DEFAULT_TEXT_CANVAS_PATTERN_ID
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.feed.presentation.FeedReelVideoPlaybackHostContent
import com.quata.feature.feed.presentation.ReelMediaSurfaceContent
import com.quata.feature.feed.presentation.VideoPlaybackFeedback
import com.quata.feature.feed.presentation.VideoPlaybackState
import com.quata.feature.feed.presentation.VideoPlaybackStrings
import com.quata.feature.feed.presentation.toggledFeedMutedState
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
    // Video may come from the public site CDN while images stay limited to the configured storage surface.
    val videoUrl = post.videoUrl?.trim()?.takeIf(::isBrowserFeedVideoUrl)
    val imageUrl = post.imageUrl?.takeIf(::isBrowserFeedMediaUrl)
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape
    when {
        videoUrl != null -> ReelMediaSurfaceContent(
            background = textCanvasBrush(seedText = null, patternId = DEFAULT_TEXT_CANVAS_PATTERN_ID),
        ) {
            BrowserFeedVideoContent(
                videoUrl = videoUrl,
                isCurrent = isCurrent,
                isMuted = isMuted,
                isLandscape = isLandscape,
                initialPositionMs = initialPositionMs,
                onPositionChanged = onPositionChanged,
                onMuteChange = onMuteChange,
            )
        }
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
    var underlayAttached by remember(videoUrl) { mutableStateOf(false) }
    val decoderAllowed = remember(videoUrl) { isBrowserFeedVideoDecoderAllowed(videoUrl) }

    fun persistPosition(milliseconds: Long) {
        positionMs = milliseconds.coerceAtLeast(0L)
        latestPositionChanged(positionMs)
    }

    fun showFeedback(value: VideoPlaybackFeedback) {
        feedback = value
        feedbackTick = currentBrowserTimeMillis()
    }

    fun play(showFeedback: Boolean) {
        playbackError = null
        isEnded = false
        val video = element
        if (video == null) {
            if (!decoderAllowed) {
                hasStartedPlayback = true
                isPlaying = true
                isBuffering = false
                if (showFeedback) showFeedback(VideoPlaybackFeedback.Play)
            }
            return
        }
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

    if (decoderAllowed) {
        DisposableEffect(videoUrl) {
            val video = (document.createElement("video") as HTMLVideoElement).apply {
                // This is a native video underlay, inserted before the Compose canvas. It contains no
                // controls and never receives pointer events; Compose owns all product UI above it.
                controls = false
                preload = "metadata"
                loop = true
                muted = isMuted
                setAttribute("playsinline", "")
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
                src = videoUrl
            }
            underlayAttached = attachBrowserFeedVideoUnderlay(video)
            element = video
            onDispose {
                released.value = true
                persistPosition((video.currentTime * 1_000.0).toLong())
                video.pause()
                clearBrowserFeedVideoListeners(video)
                detachBrowserFeedVideoUnderlay(video)
                underlayAttached = false
                if (element === video) element = null
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
            showMuteButton = true,
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
            BrowserFeedVideoUnderlayHole(
                video = element?.takeIf { underlayAttached && playbackError == null && hasStartedPlayback },
                isCurrent = isCurrent && playbackError == null && hasStartedPlayback,
                isLandscape = isLandscape,
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
        onToggleMute = { onMuteChange(toggledFeedMutedState(isMuted)) },
        modifier = Modifier.fillMaxSize(),
        controlsBottomPadding = if (isLandscape) 34.dp else 104.dp,
    )
}

@JsFun("() => Date.now()")
private external fun currentBrowserTimeMillisAsDouble(): Double

/** Date.now() is a JavaScript Number, not the BigInt required by a Wasm Kotlin Long. */
internal fun currentBrowserTimeMillis(): Long = currentBrowserTimeMillisAsDouble().toLong()

@Composable
private fun BrowserFeedVideoUnderlayHole(
    video: HTMLVideoElement?,
    isCurrent: Boolean,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
) {
    var bounds by remember(video) { mutableStateOf<BrowserFeedVideoUnderlayBounds?>(null) }
    LaunchedEffect(video, bounds, isCurrent, isLandscape) {
        val element = video ?: return@LaunchedEffect
        val frame = bounds ?: return@LaunchedEffect
        updateBrowserFeedVideoUnderlayBounds(
            video = element,
            left = frame.left,
            top = frame.top,
            width = frame.width,
            height = frame.height,
            objectFit = browserFeedVideoUnderlayObjectFit(isLandscape),
            visible = isCurrent,
        )
    }
    androidx.compose.foundation.Canvas(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                bounds = BrowserFeedVideoUnderlayBounds(
                    left = position.x,
                    top = position.y,
                    width = coordinates.size.width.toFloat(),
                    height = coordinates.size.height.toFloat(),
                )
            }
            // This command is emitted after the feed background but before the shared Compose
            // gesture layer, timeline, rail and author. It exposes only this media rectangle.
            .drawWithContent {
                if (video != null && isCurrent) {
                    drawRect(Color.Transparent, blendMode = BlendMode.Clear)
                }
            },
    ) {}
}

private data class BrowserFeedVideoUnderlayBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * The native decoder must remain below the Compose canvas even when the browser promotes the
 * video to a hardware compositor layer. The host styles changed to establish that relationship
 * are restored after the last attached decoder is removed.
 */
internal data class BrowserFeedVideoUnderlayDomContract(
    val requiresCanvasInShadowRoot: Boolean,
    val insertsBeforeCanvas: Boolean,
    val exposesHtmlProductControls: Boolean,
    val isolatesCanvasParent: Boolean,
    val composeCanvasZIndex: Int,
    val decoderZIndex: Int,
    val decoderBackgroundIsTransparent: Boolean,
    val restoresHostStylesOnDetach: Boolean,
)

internal fun browserFeedVideoUnderlayDomContract() = BrowserFeedVideoUnderlayDomContract(
    requiresCanvasInShadowRoot = true,
    insertsBeforeCanvas = true,
    exposesHtmlProductControls = false,
    isolatesCanvasParent = true,
    composeCanvasZIndex = 1,
    decoderZIndex = 0,
    decoderBackgroundIsTransparent = true,
    restoresHostStylesOnDetach = true,
)

private fun attachBrowserFeedVideoUnderlay(video: HTMLVideoElement): Boolean = js(
    """{
    const root = document.getElementById('quata-root');
    const canvas = root?.shadowRoot?.querySelector('canvas');
    const parent = canvas?.parentElement;
    if (!canvas || !parent) return false;
    video.setAttribute('aria-hidden', 'true');
    video.tabIndex = -1;
    video.style.position = 'fixed';
    video.style.pointerEvents = 'none';
    video.style.visibility = 'hidden';
    video.style.background = 'transparent';
    video.style.zIndex = '0';
    const stackingKey = '__quataFeedVideoUnderlayStacking';
    const stacking = parent[stackingKey] || {
      canvas,
      parentIsolation: parent.style.isolation,
      canvasPosition: canvas.style.position,
      canvasZIndex: canvas.style.zIndex,
      count: 0,
    };
    parent[stackingKey] = stacking;
    stacking.count += 1;
    // DOM order alone loses to a promoted HTMLVideoElement compositor layer on some browsers.
    // Isolate the siblings and put the transparent Compose canvas in an explicit higher layer.
    parent.style.isolation = 'isolate';
    canvas.style.position = 'relative';
    canvas.style.zIndex = '1';
    video.__quataFeedVideoUnderlayStacking = stacking;
    parent.insertBefore(video, canvas);
    return true;
    }""",
)

private fun updateBrowserFeedVideoUnderlayBounds(
    video: HTMLVideoElement,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    objectFit: String,
    visible: Boolean,
): Unit = js(
    """{
    video.style.left = left + 'px';
    video.style.top = top + 'px';
    video.style.width = Math.max(0, width) + 'px';
    video.style.height = Math.max(0, height) + 'px';
    video.style.objectFit = objectFit;
    video.style.visibility = visible && width > 0 && height > 0 ? 'visible' : 'hidden';
    }""",
)

private fun detachBrowserFeedVideoUnderlay(video: HTMLVideoElement): Unit = js(
    """{
    const stacking = video.__quataFeedVideoUnderlayStacking;
    if (stacking) {
      stacking.count -= 1;
      if (stacking.count === 0) {
        const parent = stacking.canvas?.parentElement;
        if (parent) {
          parent.style.isolation = stacking.parentIsolation;
          delete parent.__quataFeedVideoUnderlayStacking;
        }
        stacking.canvas.style.position = stacking.canvasPosition;
        stacking.canvas.style.zIndex = stacking.canvasZIndex;
      }
      delete video.__quataFeedVideoUnderlayStacking;
    }
    video.remove();
    }""",
)

/** Shared portrait/landscape contract for the native underlay and the pre-existing image renderer. */
internal fun browserFeedVideoUnderlayObjectFit(isLandscape: Boolean): String =
    if (isLandscape) "contain" else "cover"

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
    url.startsWith("blob:", ignoreCase = true) || isConfiguredSupabasePublicFeedMediaUrl(
        url = url,
        supabaseUrl = QuataPublicBackendConfig.SUPABASE_URL,
    )

internal fun isBrowserFeedVideoUrl(url: String): Boolean =
    url.startsWith("blob:", ignoreCase = true) ||
        isConfiguredSupabasePublicFeedMediaUrl(
            url = url,
            supabaseUrl = QuataPublicBackendConfig.SUPABASE_URL,
        ) ||
        isSafeBrowserFeedHttpsVideoUrl(url)

private fun isConfiguredSupabasePublicFeedMediaUrl(url: String, supabaseUrl: String): Boolean = js(
    """(() => {
    try {
      const candidate = new URL(url);
      const base = new URL(supabaseUrl);
      return candidate.origin === base.origin &&
        /^\/storage\/v1\/object\/public\/[^/?#]+\/[^?#]+$/i.test(candidate.pathname) &&
        !candidate.search &&
        !candidate.hash;
    } catch (_) {
      return false;
    }
    })()""",
)

private fun isSafeBrowserFeedHttpsVideoUrl(url: String): Boolean = js(
    """(() => {
    try {
      const candidate = new URL(url);
      return candidate.protocol === 'https:' &&
        !candidate.search &&
        !candidate.hash &&
        /\.(mp4|m4v|mov|webm)$/i.test(candidate.pathname);
    } catch (_) {
      return false;
    }
    })()""",
)

private fun isBrowserFeedVideoDecoderAllowed(url: String): Boolean = js(
    """(() => {
    try {
      if (!globalThis.crossOriginIsolated) return true;
      return new URL(url, globalThis.location.href).origin === globalThis.location.origin;
    } catch (_) {
      return false;
    }
    })()""",
)

internal fun isBrowserAutoplayPolicyRejection(rejection: String): Boolean =
    rejection.contains("NotAllowedError", ignoreCase = true) ||
        rejection.contains("autoplay", ignoreCase = true)

/** Reels crop in portrait and preserve the complete frame in landscape. */
internal fun browserFeedImageContentScale(isLandscape: Boolean): ContentScale =
    if (isLandscape) ContentScale.Fit else ContentScale.Crop
