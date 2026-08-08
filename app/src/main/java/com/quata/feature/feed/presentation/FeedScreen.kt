@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.feed.presentation

import android.content.Context
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import com.quata.core.ui.components.CommunityEmojiPanel
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.dismissCommunityEmojiPanelOnOutsideTap
import com.quata.core.ui.components.QuataCommentsPanel
import com.quata.core.ui.components.QuataLiveRankingPanel
import com.quata.core.ui.components.QuataFeedActionRail
import com.quata.core.ui.components.QuataFeedOverflowActionButton
import com.quata.core.ui.components.QuataFeedPullRefreshIndicator
import com.quata.core.ui.components.QuataStandardFloatingPanel
import com.quata.core.ui.components.rememberCommunityEmojiPanelDismissState
import com.quata.core.ui.components.rememberQuataFeedPullRefreshState
import com.quata.core.ui.components.trackCommunityEmojiPanelBounds
import com.quata.core.ui.components.trackCommunityEmojiTriggerBounds
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.media.QuataMediaCache
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.navigation.quataPostUrl
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.text.extractPostMeta
import com.quata.core.text.parsePostShortcodeContent
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.core.ui.components.ClickableProfileAvatar
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.CommunityEmojiPanelDismissState
import com.quata.core.ui.components.applyQuataVideoPlaybackTransform
import com.quata.core.ui.components.findQuataTextureView
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.readQuataVideoRotation
import com.quata.core.ui.components.UserAvatar
import com.quata.core.ui.components.rememberCachedRemoteImageRequest
import com.quata.core.translation.FangTranslatorIconButton
import com.quata.core.translation.LocalQuataTranslatorModeController
import com.quata.designsystem.translation.QuataTranslatorOverlaySource
import com.quata.designsystem.translation.quataTranslatableText
import com.quata.core.ui.textCanvasBrush
import com.quata.core.ui.textCanvasTypography
import com.quata.feature.feed.domain.FeedRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    padding: PaddingValues,
    feedRepository: FeedRepository,
    shareService: ShareService,
    onOpenUserProfile: (String) -> Unit,
    currentUserId: String? = null,
    openingProfileUserId: String? = null,
    focusedPostId: String? = null,
    feedResetToken: Int = 0,
    networkReconnectToken: Long = 0L,
    isNetworkAvailable: Boolean = true,
    isAppForeground: Boolean = true,
    onFocusedPostHandled: () -> Unit = {},
    onAuthRequired: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onReportComment: (String) -> Unit = {},
    onLandscapeCommentsOverlayActiveChange: (Boolean) -> Unit = {},
    viewModel: FeedAndroidViewModel = viewModel(factory = FeedAndroidViewModel.factory(feedRepository)),
) {
    val context = LocalContext.current
    val translatorModeController = LocalQuataTranslatorModeController.current
    val landscape = rememberQuataWindowLayoutInfo().isLandscape
    DisposableEffect(Unit) { onDispose { onLandscapeCommentsOverlayActiveChange(false) } }
    FeedScreenHost(
        padding = padding,
        repository = feedRepository,
        stateHolder = viewModel,
        currentUserId = currentUserId,
        focusedPostId = focusedPostId,
        feedResetToken = feedResetToken,
        networkReconnectToken = networkReconnectToken,
        isLandscape = landscape,
        onFocusedPostHandled = onFocusedPostHandled,
        onAuthRequired = onAuthRequired,
        onOpenUserProfile = onOpenUserProfile,
        onCreatePost = onCreatePost,
        onReportComment = onReportComment,
        onCommentsVisibilityChanged = onLandscapeCommentsOverlayActiveChange,
        strings = FeedScreenStrings(
            empty = stringResource(R.string.feed_empty),
            like = stringResource(R.string.feed_like), comments = stringResource(R.string.feed_comments),
            share = stringResource(R.string.feed_share), rank = stringResource(R.string.feed_rank),
            sharePostTitle = stringResource(R.string.feed_share_post),
            live = stringResource(R.string.common_live), publish = stringResource(R.string.nav_publish),
            report = stringResource(R.string.feed_report), delete = stringResource(R.string.feed_delete_post),
            deleteTitle = stringResource(R.string.feed_delete_post_confirm_title),
            deleteMessage = stringResource(R.string.feed_delete_post_confirm_message),
            reportSuccess = stringResource(R.string.feed_report_success),
            deleteSuccess = stringResource(R.string.feed_delete_post_success),
            liveTitle = stringResource(R.string.feed_live_title), liveSubtitle = stringResource(R.string.feed_live_subtitle),
            liveMonitored = { count -> stringResource(R.string.feed_live_posts_monitored, count) }, liveUpdated = stringResource(R.string.feed_live_updated), liveOpenPost = stringResource(R.string.feed_open_post),
            videoType = stringResource(R.string.feed_post_type_video), imageType = stringResource(R.string.feed_post_type_image), textType = stringResource(R.string.feed_post_type_text),
            commentsTitle = stringResource(R.string.comments_title), commentsYou = stringResource(R.string.comments_you), moderationReport = stringResource(R.string.moderation_report),
            translatorContentDescription = stringResource(R.string.translator_button_content_description),
            reply = stringResource(R.string.comments_reply_button), replyingTo = { author -> stringResource(R.string.comments_replying_to, author) }, cancelReply = stringResource(R.string.comments_cancel_reply),
            commentPlaceholder = stringResource(R.string.comments_placeholder), send = stringResource(R.string.comments_send),
            showEmojis = stringResource(R.string.comments_show_emojis),
            emojiLabels = CommunityEmojiLabels(
                recent = stringResource(R.string.emoji_recent), frequent = stringResource(R.string.emoji_frequent),
                gestures = stringResource(R.string.emoji_gestures), people = stringResource(R.string.emoji_people),
                animalsNature = stringResource(R.string.emoji_animals_nature), foodDrink = stringResource(R.string.emoji_food_drink),
                objectsSymbols = stringResource(R.string.emoji_objects_symbols), flags = stringResource(R.string.emoji_flags),
            ),
            locationLabel = { stringResource(R.string.feed_location_chip, it) },
        ),
        slots = FeedScreenPlatformSlots(
            media = { post, isCurrent, positionMs, onPositionChanged, isFeedMuted, onFeedMuteChange ->
                AndroidFeedMediaSlot(
                    post = post, isActive = isCurrent && isAppForeground, isMuted = isFeedMuted,
                    networkReconnectToken = networkReconnectToken, isNetworkAvailable = isNetworkAvailable,
                    initialVideoPositionMs = positionMs, onVideoPositionChanged = onPositionChanged, onMuteChange = onFeedMuteChange,
                )
            },
            avatar = { post ->
                ClickableProfileAvatar(
                    name = post.author.displayName, avatarUrl = post.author.avatarUrl,
                    isOfficial = post.author.isOfficial, profileId = post.author.id,
                    isLoading = openingProfileUserId == post.author.id,
                    onClick = { onOpenUserProfile(post.author.id) },
                    modifier = Modifier.size(56.dp).border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                )
            },
            share = shareService::share,
            message = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            commentsTranslatorTrigger = { _, modifier, _, _ ->
                FangTranslatorIconButton(
                    onClick = { view ->
                        translatorModeController.activate(view, QuataTranslatorOverlaySource.Comments)
                    },
                    modifier = modifier,
                )
            },
            rankingAvatar = { item -> AvatarImage(item.avatarName, item.avatarUrl, item.isOfficial, item.profileId, Modifier.size(44.dp)) },
            standardFloatingPanel = { dismiss, content -> QuataStandardFloatingPanel(onDismiss = dismiss, content = content) },
        ),
    )
}

/** Platform slot at the media-surface level; variant selection belongs exclusively to FeedScreenHost. */
@Composable
private fun AndroidFeedMediaSlot(
    post: Post, isActive: Boolean, isMuted: Boolean, networkReconnectToken: Long,
    isNetworkAvailable: Boolean, initialVideoPositionMs: Long,
    onVideoPositionChanged: (Long) -> Unit, onMuteChange: (Boolean) -> Unit,
) {
    val landscape = rememberQuataWindowLayoutInfo().isLandscape
    val videoUrl = post.videoUrl
    val imageUrl = post.imageUrl
    when {
        videoUrl != null -> ReelMediaSurfaceContent(background = textCanvasBrush(videoUrl)) {
            ReelVideo(videoUrl, isActive, isMuted, networkReconnectToken, isNetworkAvailable, initialVideoPositionMs, onVideoPositionChanged, onMuteChange)
        }
        imageUrl != null -> ReelMediaSurfaceContent(background = textCanvasBrush(imageUrl), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = rememberCachedRemoteImageRequest(imageUrl), contentDescription = post.imageTitle(),
                contentScale = if (landscape) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReelVideo(
    videoUrl: String,
    isActive: Boolean,
    isMuted: Boolean,
    networkReconnectToken: Long,
    isNetworkAvailable: Boolean,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    onMuteChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val latestIsActive by rememberUpdatedState(isActive)
    var isPlaying by rememberSaveable(videoUrl) { mutableStateOf(false) }
    var positionMs by remember(videoUrl) { mutableLongStateOf(initialPositionMs) }
    var durationMs by remember(videoUrl) { mutableLongStateOf(0L) }
    var centerFeedback by remember { mutableStateOf<VideoPlaybackFeedback?>(null) }
    var centerFeedbackTick by remember { mutableLongStateOf(0L) }
    var hasPlaybackError by remember(videoUrl) { mutableStateOf(false) }
    var isBuffering by remember(videoUrl) { mutableStateOf(false) }
    var hasStartedPlayback by remember(videoUrl) { mutableStateOf(initialPositionMs > 0L) }
    var retryCount by remember(videoUrl) { mutableStateOf(0) }
    var retrySignal by remember(videoUrl) { mutableLongStateOf(0L) }
    var playerGeneration by remember(videoUrl) { mutableLongStateOf(0L) }
    val playbackRotation by produceState(initialValue = 0, videoUrl) {
        value = withContext(Dispatchers.IO) {
            readQuataVideoRotation(context, Uri.parse(videoUrl))
        }
    }
    val mediaSourceFactory = remember(context) { QuataMediaCache.videoMediaSourceFactory(context) }
    val player = remember(videoUrl, playerGeneration) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000,
                35_000,
                1_500,
                4_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                if (initialPositionMs > 0L) {
                    seekTo(initialPositionMs)
                }
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
                volume = if (isMuted) 0f else 1f
                setFeedAudioEnabled(!isMuted)
                prepare()
            }
    }

    LaunchedEffect(networkReconnectToken, isActive) {
        if (networkReconnectToken != 0L && isActive && (hasPlaybackError || player.playbackState == Player.STATE_IDLE)) {
            hasPlaybackError = false
            retryCount = 0
            retrySignal = 0L
            isPlaying = false
            isBuffering = false
            playerGeneration = networkReconnectToken
        }
    }

    fun syncBufferingState() {
        isBuffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
    }

    fun startPlayback() {
        if (hasPlaybackError || player.playbackState == Player.STATE_IDLE) {
            hasPlaybackError = false
            player.prepare()
        }
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
        }
        player.playWhenReady = true
        player.play()
        syncBufferingState()
    }

    LaunchedEffect(player, isActive) {
        if (isActive) {
            if (!hasPlaybackError || player.playbackState == Player.STATE_IDLE) {
                startPlayback()
            }
        } else {
            player.playWhenReady = false
            player.pause()
            isPlaying = false
            isBuffering = false
        }
    }

    LaunchedEffect(player, isMuted) {
        player.setFeedAudioEnabled(!isMuted)
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(player, isActive) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            onPositionChanged(positionMs)
            if (positionMs > 0L) hasStartedPlayback = true
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            isPlaying = player.isPlaying
            delay(1_000)
        }
    }

    LaunchedEffect(player, retrySignal, isActive, isNetworkAvailable) {
        val signal = retrySignal
        if (signal == 0L || !isActive || !isNetworkAvailable) return@LaunchedEffect
        val retryDelay = when (retryCount) {
            0, 1 -> 3_000L
            2 -> 7_000L
            3 -> 12_000L
            else -> 20_000L
        }
        delay(retryDelay)
        if (latestIsActive && hasPlaybackError && retrySignal == signal) {
            hasPlaybackError = false
            startPlayback()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                if (isPlayingNow) hasStartedPlayback = true
                isPlaying = isPlayingNow
                syncBufferingState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = player.duration.takeIf { it > 0 } ?: durationMs
                if (playbackState == Player.STATE_READY) {
                    hasPlaybackError = false
                    retryCount = 0
                    if (latestIsActive && player.playWhenReady) {
                        player.play()
                    }
                }
                syncBufferingState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncBufferingState()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasPlaybackError = true
                retryCount = (retryCount + 1).coerceAtMost(5)
                isPlaying = false
                isBuffering = false
                player.playWhenReady = false
                player.pause()
                retrySignal = System.currentTimeMillis()
            }
        }
        player.addListener(listener)
        onDispose {
            onPositionChanged(player.currentPosition.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(centerFeedbackTick) {
        if (centerFeedbackTick != 0L) {
            delay(650)
            centerFeedback = null
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
            isEnded = player.playbackState == Player.STATE_ENDED,
            error = hasPlaybackError.takeIf { it }?.let { "feed_video_playback_failed" },
            feedback = centerFeedback,
        ),
        strings = VideoPlaybackStrings(
            play = stringResource(R.string.feed_play),
            pause = stringResource(R.string.feed_pause),
            mute = stringResource(R.string.feed_mute),
            unmute = stringResource(R.string.feed_unmute),
        ),
        media = {
            val videoResizeMode = if (isLandscapeLayout) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                factory = { viewContext ->
                    (LayoutInflater.from(viewContext)
                        .inflate(R.layout.quata_feed_player_texture, null, false) as PlayerView).apply {
                        this.player = player
                        useController = false
                        resizeMode = videoResizeMode
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = {
                    val applyLegacyRotationTransform =
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && playbackRotation != 0
                    it.useController = false
                    it.resizeMode = videoResizeMode
                    if (it.player !== player) {
                        it.player = player
                    }
                    it.findQuataTextureView()?.applyQuataVideoPlaybackTransform(
                        if (applyLegacyRotationTransform) playbackRotation else 0
                    )
                }
            )
        },
        onPlay = { showFeedback ->
            startPlayback()
            if (showFeedback) {
                centerFeedback = VideoPlaybackFeedback.Play
                centerFeedbackTick = System.currentTimeMillis()
            }
        },
        onPause = { showFeedback ->
            player.pause()
            isPlaying = false
            isBuffering = false
            if (showFeedback) {
                centerFeedback = VideoPlaybackFeedback.Pause
                centerFeedbackTick = System.currentTimeMillis()
            }
        },
        onSeek = { targetMs ->
            player.seekTo(targetMs)
            positionMs = targetMs
        },
        onEnded = {
            player.seekTo(0)
            startPlayback()
        },
        onError = {
            retryCount = 0
            startPlayback()
        },
        onToggleMute = { onMuteChange(toggledFeedMutedState(isMuted)) },
    )
}

private fun ExoPlayer.setFeedAudioEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !enabled)
        .build()
}

private data class PostRankingInfo(
    val position: Int,
    val likes: Int
)

private data class PostPublishedAtInfo(
    val publishedAt: LocalDateTime
)

private fun calculatePostRankingMap(posts: List<Post>): Map<String, PostRankingInfo> =
    posts
        .sortedWith(postRankingComparator())
        .mapIndexed { index, post ->
            post.id to PostRankingInfo(position = index + 1, likes = post.likesCount)
        }
        .toMap()

private fun postRankingComparator(): Comparator<Post> {
    val now = LocalDateTime.now()
    return compareByDescending<Post> { it.likesCount }
        .thenByDescending { it.rankInfo(now).publishedAt }
}

private fun Post.rankInfo(now: LocalDateTime): PostPublishedAtInfo {
    val publishedAt = parsePostCreatedAt(createdAt, now)
    return PostPublishedAtInfo(publishedAt = publishedAt)
}

private fun parsePostCreatedAt(value: String, now: LocalDateTime): LocalDateTime {
    val normalized = value.trim()
    if (normalized.isBlank() || normalized.equals("Ahora", ignoreCase = true)) return now
    if (normalized.equals("Ayer", ignoreCase = true)) return now.minusDays(1)

    parseRelativeCreatedAt(normalized, now)?.let { return it }

    return parseAbsoluteDateTime(normalized) ?: now
}

private fun parseRelativeCreatedAt(value: String, now: LocalDateTime): LocalDateTime? {
    val match = Regex("""(?i)^hace\s+(\d+)\s+([a-záéíóúñ]+)""").find(value) ?: return null
    val amount = match.groupValues[1].toLongOrNull() ?: return null
    val unit = match.groupValues[2].lowercase()
    return when {
        unit.startsWith("min") -> now.minusMinutes(amount)
        unit.startsWith("h") -> now.minusHours(amount)
        unit.startsWith("d") -> now.minusDays(amount)
        unit.startsWith("sem") -> now.minusWeeks(amount)
        else -> null
    }
}

private fun parseLocalDateTime(value: String): LocalDateTime? {
    val patterns = listOf(
        "d/M/yyyy, H:mm:ss",
        "d/M/yyyy H:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS"
    )
    patterns.forEach { pattern ->
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(pattern))
        } catch (_: DateTimeParseException) {
            // Try the next supported backend/mock format.
        }
    }
    return null
}

private fun parseAbsoluteDateTime(value: String): LocalDateTime? {
    parseLocalDateTime(value)?.let { return it }
    runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    }.getOrNull()?.let { return it }
    return runCatching {
        LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault())
    }.getOrNull()
}

private fun postShareText(post: Post): String = quataPostUrl(post.id)

private fun Post.imageTitle(): String =
    placeName?.takeIf { it.isNotBlank() } ?: rankingLabel.takeIf { it.isNotBlank() } ?: "Qüata"
