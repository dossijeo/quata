package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.quata.core.model.Post
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSMutableData
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * UIKit-only adapter for the common Feed media slot.
 *
 * FeedScreenHost decides whether a post is video, image or text. This adapter deliberately only
 * turns its already-selected remote URL into native UIKit/AVFoundation content; it has no Feed
 * state, paging or visual chrome of its own.
 */
@Composable
internal fun BoxScope.IosFeedMediaSlot(
    post: Post,
    isCurrent: Boolean,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    isMuted: Boolean,
    onMuteChange: (Boolean) -> Unit,
) {
    val videoUrl = post.videoUrl?.trim().orEmpty()
    if (videoUrl.isNotEmpty()) {
        IosFeedVideoPlayback(
            videoUrl = videoUrl,
            isCurrent = isCurrent,
            initialPositionMs = initialPositionMs,
            onPositionChanged = onPositionChanged,
            isMuted = isMuted,
            onMuteChange = onMuteChange,
        )
    } else {
        IosFeedImage(
            imageUrl = post.imageUrl?.trim().orEmpty(),
            modifier = Modifier.matchParentSize(),
        )
    }
}

/** The control layer is the exact common Compose host used by Android, not a UIKit substitute. */
@Composable
private fun IosFeedVideoPlayback(
    videoUrl: String,
    isCurrent: Boolean,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    isMuted: Boolean,
    onMuteChange: (Boolean) -> Unit,
) {
    var nativeView by remember(videoUrl) { mutableStateOf<IosFeedMediaView?>(null) }
    var playback by remember(videoUrl) {
        mutableStateOf(IosVideoPlaybackSnapshot(positionMs = initialPositionMs, hasStartedPlayback = initialPositionMs > 0L))
    }
    var feedback by remember(videoUrl) { mutableStateOf<VideoPlaybackFeedback?>(null) }
    var feedbackTick by remember(videoUrl) { mutableStateOf(0L) }
    val landscape = rememberQuataWindowLayoutInfo().isLandscape

    LaunchedEffect(feedbackTick) {
        if (feedbackTick != 0L) {
            delay(650)
            feedback = null
        }
    }
    LaunchedEffect(isCurrent, nativeView) {
        nativeView?.setPlaybackActive(isCurrent)
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
        strings = VideoPlaybackStrings(play = "Reproducir", pause = "Pausar", mute = "Silenciar", unmute = "Activar sonido"),
        media = {
            UIKitView(
                factory = {
                    IosFeedMediaView().also { nativeView = it }
                },
                update = { view ->
                    nativeView = view
                    view.renderVideo(
                        url = videoUrl,
                        isCurrent = isCurrent,
                        initialPositionMs = initialPositionMs,
                        isMuted = isMuted,
                        onSnapshot = { snapshot ->
                            playback = snapshot
                            onPositionChanged(snapshot.positionMs)
                        },
                    )
                },
                onRelease = { view ->
                    if (nativeView === view) nativeView = null
                    view.dispose()
                },
                modifier = Modifier.matchParentSize(),
            )
        },
        onPlay = { showFeedback ->
            nativeView?.play()
            if (showFeedback) {
                feedback = VideoPlaybackFeedback.Play
                feedbackTick += 1L
            }
        },
        onPause = { showFeedback ->
            nativeView?.pause()
            if (showFeedback) {
                feedback = VideoPlaybackFeedback.Pause
                feedbackTick += 1L
            }
        },
        onSeek = { targetMs -> nativeView?.seekTo(targetMs) },
        onEnded = { nativeView?.restart() },
        onError = { nativeView?.retry() },
        onToggleMute = { onMuteChange(!isMuted) },
        modifier = Modifier.matchParentSize(),
    )
}

@Composable
private fun IosFeedImage(imageUrl: String, modifier: Modifier) {
    UIKitView(
        factory = { IosFeedMediaView() },
        update = { view -> view.renderImage(imageUrl) },
        onRelease = IosFeedMediaView::dispose,
        modifier = modifier,
    )
}

private data class IosVideoPlaybackSnapshot(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasStartedPlayback: Boolean = false,
    val isEnded: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalForeignApi::class)
private class IosFeedMediaView : UIView() {
    private val imageView = UIImageView().apply {
        clipsToBounds = true
        contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
    }
    private var imageTask: NSURLSessionDataTask? = null
    private var imageSession: NSURLSession? = null
    private var imageDelegate: IosFeedImageTaskDelegate? = null
    private var imageRequestId = 0L
    private var displayedImageUrl: String? = null

    private var player: AVPlayer? = null
    private var playerLayer: AVPlayerLayer? = null
    private var playerUrl: String? = null
    private var periodicObserver: Any? = null
    private var endObserver: Any? = null
    private var onSnapshot: ((IosVideoPlaybackSnapshot) -> Unit)? = null
    private var requestedPlayback = false
    private var playbackStarted = false
    private var reportedPositionMs = 0L

    init {
        addSubview(imageView)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        imageView.setFrame(bounds)
        playerLayer?.setFrame(bounds)
        // Android's ReelMedia uses crop in portrait and fit in landscape. The shared slot owns no
        // orientation parameter, but its final bounds tell UIKit the same thing without adding a
        // second Feed layout implementation.
        val isLandscapeSlot = bounds.useContents { size.width > size.height }
        imageView.contentMode = if (isLandscapeSlot) {
            UIViewContentMode.UIViewContentModeScaleAspectFit
        } else {
            UIViewContentMode.UIViewContentModeScaleAspectFill
        }
        playerLayer?.videoGravity = if (isLandscapeSlot) {
            AVLayerVideoGravityResizeAspect
        } else {
            AVLayerVideoGravityResizeAspectFill
        }
    }

    fun renderImage(url: String) {
        releasePlayer(reportPosition = true)
        if (displayedImageUrl == url) return
        displayedImageUrl = url
        imageTask?.cancel()
        imageSession?.invalidateAndCancel()
        imageSession = null
        imageDelegate = null
        imageView.image = null
        val requestId = ++imageRequestId
        val remoteUrl = NSURL(string = url) ?: return
        val delegate = IosFeedImageTaskDelegate { data ->
            val image = data?.let { bytes -> UIImage.imageWithData(bytes) }
            if (image == null) return@IosFeedImageTaskDelegate
            dispatch_async(dispatch_get_main_queue()) {
                if (requestId == imageRequestId && displayedImageUrl == url) imageView.image = image
            }
        }
        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.ephemeralSessionConfiguration(),
            delegate,
            null,
        )
        imageDelegate = delegate
        imageSession = session
        imageTask = session.dataTaskWithURL(remoteUrl).also { it.resume() }
    }

    fun renderVideo(
        url: String,
        isCurrent: Boolean,
        initialPositionMs: Long,
        isMuted: Boolean,
        onSnapshot: (IosVideoPlaybackSnapshot) -> Unit,
    ) {
        this.onSnapshot = onSnapshot
        imageTask?.cancel()
        imageTask = null
        imageSession?.invalidateAndCancel()
        imageSession = null
        imageDelegate = null
        displayedImageUrl = null
        imageView.image = null
        if (playerUrl != url) {
            releasePlayer(reportPosition = false)
            val remoteUrl = NSURL(string = url) ?: return
            // Kotlin/Native keeps the Objective-C selector casing here (`uRL`), rather than
            // Swift's `url`; using the designated initializer also exposes the full AVPlayer API.
            val newPlayer = AVPlayer(uRL = remoteUrl)
            val newLayer = AVPlayerLayer.playerLayerWithPlayer(newPlayer)
            player = newPlayer
            playerLayer = newLayer
            playerUrl = url
            layer.addSublayer(newLayer)
            newLayer.setFrame(bounds)
            installPositionObserver(newPlayer)
            installLoopObserver(newPlayer)
            if (initialPositionMs > 0L) newPlayer.seekToTime(CMTimeMake(initialPositionMs, 1_000))
        }
        player?.muted = isMuted
        setPlaybackActive(isCurrent)
    }

    fun setPlaybackActive(isActive: Boolean) {
        if (isActive) play() else pause()
    }

    fun play() {
        requestedPlayback = true
        player?.play()
        emitSnapshot()
    }

    fun pause() {
        requestedPlayback = false
        player?.pause()
        emitSnapshot()
    }

    fun seekTo(positionMs: Long) {
        player?.seekToTime(CMTimeMake(positionMs.coerceAtLeast(0L), 1_000))
        reportedPositionMs = positionMs.coerceAtLeast(0L)
        emitSnapshot()
    }

    fun restart() {
        seekTo(0L)
        play()
    }

    fun retry() {
        val url = playerUrl ?: return
        val muted = player?.muted == true
        val callback = onSnapshot ?: return
        releasePlayer(reportPosition = false)
        renderVideo(url, isCurrent = true, initialPositionMs = 0L, isMuted = muted, onSnapshot = callback)
    }

    private fun installPositionObserver(activePlayer: AVPlayer) {
        periodicObserver = activePlayer.addPeriodicTimeObserverForInterval(
            interval = CMTimeMake(1, 2),
            queue = dispatch_get_main_queue(),
        ) { time ->
            reportedPositionMs = (CMTimeGetSeconds(time) * 1_000.0).toLong().coerceAtLeast(0L)
            if (reportedPositionMs > 0L) playbackStarted = true
            emitSnapshot()
        }
    }

    private fun installLoopObserver(activePlayer: AVPlayer) {
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            AVPlayerItemDidPlayToEndTimeNotification,
            activePlayer.currentItem,
            null,
        ) {
            activePlayer.seekToTime(CMTimeMake(0L, 1_000))
            if (requestedPlayback) activePlayer.play()
            emitSnapshot()
        }
    }

    private fun emitSnapshot() {
        val activePlayer = player ?: return
        val duration = CMTimeGetSeconds(activePlayer.currentItem?.duration ?: CMTimeMake(0L, 1_000))
            .takeIf { it.isFinite() && it > 0.0 }?.times(1_000.0)?.toLong() ?: 0L
        onSnapshot?.invoke(
            IosVideoPlaybackSnapshot(
                isPlaying = activePlayer.rate > 0f,
                isBuffering = requestedPlayback && activePlayer.rate <= 0f && duration > 0L,
                positionMs = reportedPositionMs,
                durationMs = duration,
                hasStartedPlayback = playbackStarted,
            ),
        )
    }

    fun dispose() {
        imageRequestId += 1
        imageTask?.cancel()
        imageTask = null
        imageSession?.invalidateAndCancel()
        imageSession = null
        imageDelegate = null
        displayedImageUrl = null
        imageView.image = null
        releasePlayer(reportPosition = true)
        onSnapshot = null
    }

    private fun releasePlayer(reportPosition: Boolean) {
        val activePlayer = player
        if (reportPosition && activePlayer != null) {
            val milliseconds = (CMTimeGetSeconds(activePlayer.currentTime()) * 1_000.0).toLong()
            if (milliseconds >= 0L) {
                reportedPositionMs = milliseconds
                emitSnapshot()
            }
        }
        periodicObserver?.let { observer -> activePlayer?.removeTimeObserver(observer) }
        periodicObserver = null
        endObserver?.let { observer -> NSNotificationCenter.defaultCenter.removeObserver(observer) }
        endObserver = null
        activePlayer?.pause()
        playerLayer?.removeFromSuperlayer()
        playerLayer = null
        player = null
        playerUrl = null
        requestedPlayback = false
        playbackStarted = false
        reportedPositionMs = 0L
    }
}

/** NSURLSession completion-handler overloads are not exported consistently by Kotlin/Native. */
@OptIn(ExperimentalForeignApi::class)
private class IosFeedImageTaskDelegate(
    private val onComplete: (NSData?) -> Unit,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val bytes = NSMutableData()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        bytes.appendData(didReceiveData)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        onComplete(bytes.takeIf { didCompleteWithError == null && it.length > 0uL })
    }
}
