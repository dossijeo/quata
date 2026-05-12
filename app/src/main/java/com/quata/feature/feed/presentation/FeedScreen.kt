package com.quata.feature.feed.presentation

import android.content.Intent
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.textCanvasBrush
import com.quata.feature.feed.domain.FeedRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    padding: PaddingValues,
    feedRepository: FeedRepository,
    viewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory(feedRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var commentsPost by remember { mutableStateOf<Post?>(null) }
    var isLiveOpen by remember { mutableStateOf(false) }
    val localComments = remember { mutableStateMapOf<String, List<PostComment>>() }

    when {
        state.error != null -> FeedMessageScreen(padding, state.error ?: "", onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) })
        state.posts.isEmpty() && !state.isLoading -> FeedMessageScreen(padding, stringResource(R.string.feed_empty), onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) })
        else -> {
            val pagerState = rememberPagerState(pageCount = { state.posts.size })
            val postRanks = remember(state.posts) { calculateDailyPostRanks(state.posts) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.Black)
            ) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val post = state.posts[page]
                    val extraComments = localComments[post.id].orEmpty()
                    ReelPost(
                        post = post,
                        postRank = postRanks[post.id] ?: 1,
                        isCurrentPage = pagerState.currentPage == page,
                        extraCommentsCount = extraComments.size,
                        onOpenComments = { commentsPost = post },
                        onOpenLive = { isLiveOpen = true },
                        onShare = {
                            val shareText = postShareText(post)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.feed_share_post)))
                        },
                        onReport = {
                            Toast.makeText(context, context.getString(R.string.feed_report_success), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            commentsPost?.let { post ->
                CommentsSheet(
                    post = post,
                    localComments = localComments[post.id].orEmpty(),
                    onAddComment = { comment ->
                        localComments[post.id] = localComments[post.id].orEmpty() + comment
                    },
                    onDismiss = { commentsPost = null }
                )
            }

            if (isLiveOpen) {
                LiveRankingDialog(
                    posts = state.posts,
                    postRanks = postRanks,
                    onDismiss = { isLiveOpen = false },
                    onOpenPost = { post ->
                        val index = state.posts.indexOfFirst { it.id == post.id }
                        if (index >= 0) {
                            isLiveOpen = false
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FeedMessageScreen(
    padding: PaddingValues,
    message: String,
    onRefresh: () -> Unit
) {
    QuataScreen(padding) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(message, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh))
                }
            }
        }
    }
}

@Composable
private fun LiveRankingDialog(
    posts: List<Post>,
    postRanks: Map<String, Int>,
    onDismiss: () -> Unit,
    onOpenPost: (Post) -> Unit
) {
    val rankedPosts = remember(posts) {
        posts.sortedWith(feedRankingComparator())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF111827),
            contentColor = Color.White,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(580.dp)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.feed_live_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(R.string.feed_live_subtitle),
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = 14.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.feed_live_posts_monitored, posts.size),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = stringResource(R.string.feed_live_updated),
                                color = Color.White.copy(alpha = 0.64f)
                            )
                        }
                        ReelChip(text = stringResource(R.string.common_live), highlighted = true)
                    }
                }
                Spacer(Modifier.height(18.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rankedPosts) { post ->
                        LiveRankingRow(
                            rank = postRanks[post.id] ?: (rankedPosts.indexOf(post) + 1),
                            post = post,
                            onOpenPost = { onOpenPost(post) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveRankingRow(
    rank: Int,
    post: Post,
    onOpenPost: () -> Unit
) {
    val borderColor = when (rank) {
        1 -> Color(0xFFE5D45C)
        2 -> Color.White.copy(alpha = 0.26f)
        3 -> QuataOrange.copy(alpha = 0.8f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            color = Color(0xFFFFF29E),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.width(38.dp)
        )
        AvatarLetter(post.author.displayName, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = post.author.displayName,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = postTypeLabel(post),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.width(86.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♥", color = Color(0xFFFF5A8E), fontSize = 18.sp)
                Spacer(Modifier.width(4.dp))
                Text(post.likesCount.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .width(86.dp)
                    .height(38.dp)
                    .clickable(onClick = onOpenPost)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.feed_open_post), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun postTypeLabel(post: Post): String = when {
    post.videoUrl != null -> stringResource(R.string.feed_post_type_video)
    post.imageUrl != null -> stringResource(R.string.feed_post_type_image)
    else -> stringResource(R.string.feed_post_type_text)
}

@Composable
private fun ReelPost(
    post: Post,
    postRank: Int,
    isCurrentPage: Boolean,
    extraCommentsCount: Int,
    onOpenComments: () -> Unit,
    onOpenLive: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit
) {
    val isVideo = post.videoUrl != null
    val isTextOnly = post.videoUrl == null && post.imageUrl == null && post.text.isNotBlank()
    var isVideoMuted by rememberSaveable(post.id) { mutableStateOf(true) }
    var isDescriptionExpanded by rememberSaveable(post.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        ReelMedia(
            post = post,
            isActive = isCurrentPage,
            isMuted = isVideoMuted,
            onMuteChange = { isVideoMuted = it }
        )
        ReelScrims()
        ReelTopChips(
            post = post,
            postRank = postRank,
            showLocation = !isTextOnly && !isVideo,
            isVideo = isVideo,
            isMuted = isVideoMuted,
            onToggleMute = { isVideoMuted = !isVideoMuted },
            onOpenLive = onOpenLive
        )
        ReelActions(
            likes = post.likesCount,
            comments = post.comments.size + extraCommentsCount,
            onOpenComments = onOpenComments,
            onShare = onShare,
            onReport = onReport,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp)
        )
        ReelAuthor(
            post = post,
            showDescription = isVideo && post.text.isNotBlank(),
            isDescriptionExpanded = isDescriptionExpanded,
                onToggleDescription = { isDescriptionExpanded = !isDescriptionExpanded },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, end = 96.dp, bottom = if (isVideo) 82.dp else 20.dp)
        )
    }
}

@Composable
private fun ReelMedia(
    post: Post,
    isActive: Boolean,
    isMuted: Boolean,
    onMuteChange: (Boolean) -> Unit
) {
    when {
        post.videoUrl != null -> ReelVideo(
            videoUrl = post.videoUrl,
            isActive = isActive,
            isMuted = isMuted,
            onMuteChange = onMuteChange
        )
        post.imageUrl != null -> {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = post.text,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        post.text.isNotBlank() -> TextOnlyReel(post = post)

        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF78B7E8),
                                Color(0xFF2E6F95),
                                Color(0xFF16202D)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun TextOnlyReel(post: Post) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(textCanvasBrush(post.text))
            .padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = post.text,
            color = Color.White,
            fontSize = 34.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun ReelVideo(
    videoUrl: String,
    isActive: Boolean,
    isMuted: Boolean,
    onMuteChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var isPlaying by rememberSaveable(videoUrl) { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var centerFeedbackIcon by remember { mutableStateOf<ImageVector?>(null) }
    var centerFeedbackTick by remember { mutableLongStateOf(0L) }
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
            volume = 0f
            prepare()
        }
    }

    LaunchedEffect(player, isActive) {
        player.playWhenReady = isActive
        if (isActive) player.play() else player.pause()
        isPlaying = isActive
    }

    LaunchedEffect(player, isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(player, isActive) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(centerFeedbackTick) {
        if (centerFeedbackTick != 0L) {
            delay(650)
            centerFeedbackIcon = null
        }
    }

    fun togglePlayback(showFeedback: Boolean) {
        if (player.isPlaying) {
            player.pause()
            isPlaying = false
            if (showFeedback) centerFeedbackIcon = Icons.Filled.Pause
        } else {
            player.play()
            isPlaying = true
            if (showFeedback) centerFeedbackIcon = Icons.Filled.PlayArrow
        }
        if (showFeedback) centerFeedbackTick = System.currentTimeMillis()
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { togglePlayback(showFeedback = true) }
        )
        centerFeedbackIcon?.let { icon ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
        }
        VideoControls(
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            isMuted = isMuted,
            onPlayPause = { togglePlayback(showFeedback = false) },
            onSeek = { targetMs ->
                player.seekTo(targetMs)
                positionMs = targetMs
            },
            onToggleMute = { onMuteChange(!isMuted) },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 96.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isMuted: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val duration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(R.string.feed_pause)
                } else {
                    stringResource(R.string.feed_play)
                },
                tint = Color.White
            )
        }
        TimelineThumb(
            progress = progress,
            onProgressChange = { onSeek((it * duration).toLong()) },
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
        )
        Text(
            text = "${formatVideoTime(positionMs)} / ${formatVideoTime(durationMs)}",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.width(82.dp)
        )
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) {
                        stringResource(R.string.feed_unmute)
                    } else {
                        stringResource(R.string.feed_mute)
                    },
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun TimelineThumb(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onProgressChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .offset(x = (maxWidth - 10.dp) * progress)
                .clip(CircleShape)
                .background(QuataOrange)
        )
    }
}

private fun formatVideoTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun ReelScrims() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.36f),
                    0.25f to Color.Transparent,
                    0.58f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.68f)
                )
            )
    )
}

@Composable
private fun ReelTopChips(
    post: Post,
    postRank: Int,
    showLocation: Boolean,
    isVideo: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onOpenLive: () -> Unit
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 20.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showLocation && post.placeName != null) {
            ReelChip(text = stringResource(R.string.feed_location_chip, post.placeName))
        }
        ReelChip(text = stringResource(R.string.feed_rank_chip, postRank, post.likesCount), highlighted = true)
        ReelChip(text = stringResource(R.string.common_live), highlighted = true, onClick = onOpenLive)
        if (isVideo) {
            ReelRoundChip(
                isMuted = isMuted,
                onClick = onToggleMute
            )
        }
    }
}

@Composable
private fun ReelRoundChip(
    isMuted: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color(0xFFE5D45C), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (isMuted) {
                stringResource(R.string.feed_unmute)
            } else {
                stringResource(R.string.feed_mute)
            },
            tint = Color(0xFFFFF29E),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ReelChip(
    text: String,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val borderColor = if (highlighted) Color(0xFFE5D45C) else Color.White.copy(alpha = 0.22f)
    val textColor = if (highlighted) Color(0xFFFFF29E) else Color.White

    Surface(
        color = Color.White.copy(alpha = if (highlighted) 0.16f else 0.12f),
        contentColor = textColor,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(28.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun ReelActions(
    likes: Int,
    comments: Int,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var liked by rememberSaveable { mutableStateOf(false) }
    val visibleLikes = likes + if (liked) 1 else 0

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ReelActionButton(
            icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.feed_like),
            count = visibleLikes.toString(),
            tint = if (liked) Color(0xFFFF7EA8) else Color.White,
            onClick = { liked = !liked }
        )
        ReelActionButton(
            icon = Icons.Filled.ChatBubble,
            contentDescription = stringResource(R.string.feed_comments),
            count = comments.toString(),
            onClick = onOpenComments
        )
        ReelActionButton(
            icon = Icons.Filled.Share,
            contentDescription = stringResource(R.string.feed_share),
            onClick = onShare
        )
        ReelActionButton(
            icon = Icons.Filled.Flag,
            contentDescription = stringResource(R.string.feed_report),
            tint = Color.White,
            onClick = onReport
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    post: Post,
    localComments: List<PostComment>,
    onAddComment: (PostComment) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var draft by rememberSaveable(post.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var replyTarget by remember { mutableStateOf<PostComment?>(null) }
    var isEmojiPickerVisible by rememberSaveable(post.id) { mutableStateOf(false) }
    var shouldScrollToCommentsEnd by remember { mutableStateOf(true) }
    val commentsListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.PartiallyExpanded }
    )
    val comments = remember(post.id, localComments) {
        post.comments + localComments
    }

    LaunchedEffect(post.id) {
        sheetState.expand()
        shouldScrollToCommentsEnd = true
    }

    LaunchedEffect(comments.size, shouldScrollToCommentsEnd) {
        if (shouldScrollToCommentsEnd && comments.isNotEmpty()) {
            delay(260)
            commentsListState.animateScrollToItem(comments.size)
            shouldScrollToCommentsEnd = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF11141D),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(start = 20.dp, end = 20.dp, bottom = 48.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.comments_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.68f)
                )
                Spacer(Modifier.width(10.dp))
                Text("💬", fontSize = 16.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = comments.size.toString(),
                    color = Color.White.copy(alpha = 0.74f),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                state = commentsListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 180.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(comments) { comment ->
                    CommentRow(
                        comment = comment,
                        onReply = {
                            replyTarget = comment
                        }
                    )
                }
                item(key = "comments-end") {
                    Spacer(Modifier.height(24.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            if (isEmojiPickerVisible) {
                EmojiPicker { emoji ->
                    draft = draft.insertAtSelection(emoji)
                    isEmojiPickerVisible = false
                }
                Spacer(Modifier.height(18.dp))
            }
            replyTarget?.let { target ->
                ReplyTargetBanner(
                    comment = target,
                    onClear = { replyTarget = null }
                )
                Spacer(Modifier.height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.comments_placeholder)) },
                    leadingIcon = {
                        IconButton(onClick = { isEmojiPickerVisible = !isEmojiPickerVisible }) {
                            Icon(
                                imageVector = Icons.Filled.InsertEmoticon,
                                contentDescription = stringResource(R.string.comments_show_emojis),
                                tint = Color(0xFFFFC55C)
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            enabled = draft.text.isNotBlank(),
                            onClick = {
                                onAddComment(
                                    PostComment(
                                        id = "local_${post.id}_${System.currentTimeMillis()}",
                                        authorName = context.getString(R.string.comments_you),
                                        message = draft.text.trim(),
                                        timestamp = nowCommentTimestamp(),
                                        replyToAuthorName = replyTarget?.authorName,
                                        replyToMessage = replyTarget?.message
                                    )
                                )
                                shouldScrollToCommentsEnd = true
                                draft = TextFieldValue("")
                                replyTarget = null
                                isEmojiPickerVisible = false
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.comments_send)
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 58.dp),
                    singleLine = true
                )
            }
        }
    }
}

private fun TextFieldValue.insertAtSelection(value: String): TextFieldValue {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val replaceStart = minOf(start, end)
    val replaceEnd = maxOf(start, end)
    val updatedText = text.replaceRange(replaceStart, replaceEnd, value)
    val cursor = replaceStart + value.length
    return TextFieldValue(
        text = updatedText,
        selection = TextRange(cursor)
    )
}

@Composable
private fun ReplyTargetBanner(
    comment: PostComment,
    onClear: () -> Unit
) {
    Surface(
        color = QuataOrange.copy(alpha = 0.08f),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, QuataOrange.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.comments_replying_to, comment.authorName),
                    color = Color.White.copy(alpha = 0.94f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = comment.message,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.comments_cancel_reply))
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: PostComment,
    onReply: () -> Unit
) {
    Surface(
        color = Color.White.copy(alpha = 0.055f),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(14.dp)
        ) {
            if (comment.replyToAuthorName != null) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(Color(0xFF83DCFF))
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = comment.authorName,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                        comment.replyToAuthorName?.let { author ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.comments_reply_to, author),
                                color = Color(0xFF83DCFF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = comment.message,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            lineHeight = 21.sp
                        )
                    }
                    Text(
                        text = comment.timestamp,
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp
                    )
                }
                TextButton(
                    onClick = onReply,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.comments_reply_button),
                        color = Color(0xFF83DCFF),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiPicker(onEmojiClick: (String) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, QuataOrange.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(listOf("😀", "😍", "😂", "🥰", "👏", "🙌", "🔥", "❤️", "👍", "🙏")) { emoji ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onEmojiClick(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 24.sp)
            }
        }
    }
}

private fun nowCommentTimestamp(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yyyy, H:mm:ss"))

private data class PostRankInfo(
    val day: LocalDate,
    val publishedAt: LocalDateTime
)

private fun calculateDailyPostRanks(posts: List<Post>): Map<String, Int> {
    val now = LocalDateTime.now()
    return posts
        .map { post -> post to post.rankInfo(now) }
        .groupBy { it.second.day }
        .flatMap { (_, dayPosts) ->
            dayPosts
                .sortedWith(
                    compareByDescending<Pair<Post, PostRankInfo>> { it.first.likesCount }
                        .thenByDescending { it.second.publishedAt }
                        .thenBy { it.first.id }
                )
                .mapIndexed { index, (post, _) -> post.id to index + 1 }
        }
        .toMap()
}

private fun feedRankingComparator(): Comparator<Post> {
    val now = LocalDateTime.now()
    return compareByDescending<Post> { it.rankInfo(now).day }
        .thenByDescending { it.likesCount }
        .thenByDescending { it.rankInfo(now).publishedAt }
        .thenBy { it.id }
}

private fun Post.rankInfo(now: LocalDateTime): PostRankInfo {
    val publishedAt = parsePostCreatedAt(createdAt, now)
    return PostRankInfo(day = publishedAt.toLocalDate(), publishedAt = publishedAt)
}

private fun parsePostCreatedAt(value: String, now: LocalDateTime): LocalDateTime {
    val normalized = value.trim()
    if (normalized.isBlank() || normalized.equals("Ahora", ignoreCase = true)) return now
    if (normalized.equals("Ayer", ignoreCase = true)) return now.minusDays(1)

    parseRelativeCreatedAt(normalized, now)?.let { return it }

    parseLocalDateTime(normalized)?.let { return it }

    return try {
        LocalDateTime.ofInstant(Instant.parse(normalized), ZoneId.systemDefault())
    } catch (_: DateTimeParseException) {
        now
    }
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

@Composable
private fun ReelActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    count: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(25.dp)
                )
                if (count != null) {
                    Text(
                        text = count,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

private fun postShareText(post: Post): String = buildString {
    append(post.author.displayName)
    append(": ")
    append(post.text)
    post.imageUrl?.let {
        append("\n")
        append(it)
    }
    post.videoUrl?.let {
        append("\n")
        append(it)
    }
}

@Composable
private fun ReelAuthor(
    post: Post,
    showDescription: Boolean,
    isDescriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        AvatarLetter(
            name = post.author.displayName,
            modifier = Modifier
                .size(56.dp)
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = post.author.displayName,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
            Text(
                text = post.createdAt,
                color = QuataOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (showDescription) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = post.text,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onToggleDescription)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}
