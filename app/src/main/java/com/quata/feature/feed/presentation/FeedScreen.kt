package com.quata.feature.feed.presentation

import android.graphics.Color as AndroidColor
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import com.quata.core.ui.components.CommunityEmojiPanel
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.dismissCommunityEmojiPanelOnOutsideTap
import com.quata.core.ui.components.rememberCommunityEmojiPanelDismissState
import com.quata.core.ui.components.trackCommunityEmojiPanelBounds
import com.quata.core.ui.components.trackCommunityEmojiTriggerBounds
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.QuataThemeTemplate
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.media.QuataMediaCache
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.navigation.quataPostUrl
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.text.extractPostMeta
import com.quata.core.text.parsePostShortcodeContent
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.core.ui.components.ClickableProfileAvatar
import com.quata.core.ui.components.CommunityEmojiPanelDismissState
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.UserAvatar
import com.quata.core.ui.components.rememberCachedRemoteImageRequest
import com.quata.core.translation.FangTranslatorIconButton
import com.quata.core.translation.LocalQuataTranslatorModeController
import com.quata.core.translation.QuataTranslatorOverlaySource
import com.quata.core.translation.quataTranslatableText
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    padding: PaddingValues,
    feedRepository: FeedRepository,
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
    onLandscapeCommentsOverlayActiveChange: (Boolean) -> Unit = {},
    viewModel: FeedViewModel = viewModel(factory = FeedViewModel.factory(feedRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var commentsPost by remember { mutableStateOf<Post?>(null) }
    var isLiveOpen by remember { mutableStateOf(false) }
    var postPendingDeletion by remember { mutableStateOf<Post?>(null) }
    var pendingDeletedPostId by remember { mutableStateOf<String?>(null) }
    var isFeedMuted by rememberSaveable { mutableStateOf(false) }
    val canParticipate = currentUserId != null
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape

    LaunchedEffect(commentsPost, isLandscapeLayout) {
        onLandscapeCommentsOverlayActiveChange(commentsPost != null && isLandscapeLayout)
    }

    DisposableEffect(Unit) {
        onDispose { onLandscapeCommentsOverlayActiveChange(false) }
    }

    LaunchedEffect(networkReconnectToken) {
        if (networkReconnectToken != 0L) {
            viewModel.onEvent(FeedUiEvent.Refresh)
        }
    }

    LaunchedEffect(state.posts, pendingDeletedPostId) {
        val deletedPostId = pendingDeletedPostId ?: return@LaunchedEffect
        if (state.posts.none { it.id == deletedPostId }) {
            Toast.makeText(context, context.getString(R.string.feed_delete_post_success), Toast.LENGTH_SHORT).show()
            pendingDeletedPostId = null
        }
    }

    when {
        state.error != null && state.posts.isEmpty() -> FeedMessageScreen(padding, state.error ?: "", onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) })
        state.posts.isEmpty() && !state.isLoading -> FeedMessageScreen(padding, stringResource(R.string.feed_empty), onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) })
        else -> {
            val pagerState = rememberPagerState(pageCount = { state.posts.size })
            val postRanks = remember(state.posts) { calculatePostRankingMap(state.posts) }
            val videoPositions = remember { mutableMapOf<String, Long>() }
            var handledFocusedPostId by rememberSaveable { mutableStateOf<String?>(null) }
            val density = LocalDensity.current
            var pullRefreshDistancePx by remember { mutableFloatStateOf(0f) }
            val pullRefreshTriggerPx = with(density) { FeedPullRefreshTriggerDistance.toPx() }
            val pullRefreshMaxPx = with(density) { FeedPullRefreshMaxDistance.toPx() }
            val canPullRefreshState = rememberUpdatedState(
                pagerState.currentPage == 0 &&
                    !state.isRefreshing &&
                    commentsPost == null &&
                    !isLiveOpen
            )
            val isRefreshingState = rememberUpdatedState(state.isRefreshing)
            val requestRefreshState = rememberUpdatedState {
                viewModel.onEvent(FeedUiEvent.Refresh)
            }
            val pullRefreshConnection = remember(pullRefreshTriggerPx, pullRefreshMaxPx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        val deltaY = available.y
                        if (deltaY > 0f && canPullRefreshState.value) {
                            val resistance = 1f - (pullRefreshDistancePx / pullRefreshMaxPx).coerceIn(0f, 0.72f)
                            pullRefreshDistancePx = (pullRefreshDistancePx + deltaY * resistance).coerceAtMost(pullRefreshMaxPx)
                            return Offset(0f, deltaY)
                        }
                        if (deltaY < 0f && pullRefreshDistancePx > 0f) {
                            val consumed = minOf(pullRefreshDistancePx, -deltaY)
                            pullRefreshDistancePx -= consumed
                            return Offset(0f, -consumed)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (pullRefreshDistancePx >= pullRefreshTriggerPx && !isRefreshingState.value) {
                            pullRefreshDistancePx = pullRefreshTriggerPx
                            requestRefreshState.value()
                        } else if (!isRefreshingState.value) {
                            pullRefreshDistancePx = 0f
                        }
                        return Velocity.Zero
                    }
                }
            }

            LaunchedEffect(state.isRefreshing, pagerState.currentPage, pullRefreshTriggerPx) {
                if (state.isRefreshing && pagerState.currentPage == 0 && pullRefreshDistancePx < pullRefreshTriggerPx) {
                    pullRefreshDistancePx = pullRefreshTriggerPx
                } else if (!state.isRefreshing) {
                    pullRefreshDistancePx = 0f
                }
            }

            LaunchedEffect(focusedPostId) {
                if (focusedPostId != null && focusedPostId != handledFocusedPostId) {
                    viewModel.onEvent(FeedUiEvent.Refresh)
                }
            }

            LaunchedEffect(focusedPostId, state.posts) {
                val targetId = focusedPostId ?: return@LaunchedEffect
                if (targetId == handledFocusedPostId) return@LaunchedEffect
                val targetIndex = state.posts.indexOfFirst { it.id == targetId }
                if (targetIndex >= 0) {
                    pagerState.scrollToPage(targetIndex)
                    handledFocusedPostId = targetId
                    onFocusedPostHandled()
                }
            }

            LaunchedEffect(feedResetToken, state.posts.size) {
                if (feedResetToken != 0 && focusedPostId == null && state.posts.isNotEmpty()) {
                    pagerState.scrollToPage(0)
                }
            }

            val visiblePostId = state.posts.getOrNull(pagerState.currentPage)?.id
            val nextPostId = state.posts.getOrNull(pagerState.currentPage + 1)?.id
            LaunchedEffect(visiblePostId, nextPostId) {
                visiblePostId?.let { postId ->
                    viewModel.onEvent(FeedUiEvent.PostDisplayed(postId, nextPostId))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(pullRefreshConnection)
                    .background(Color.Black)
            ) {
                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val post = state.posts[page]
                    val videoPositionKey = post.videoUrl
                    key(post.id, post.videoUrl) {
                        ReelPost(
                            post = post,
                            postRankInfo = postRanks[post.id] ?: PostRankingInfo(position = 1, likes = post.likesCount),
                            isCurrentPage = pagerState.currentPage == page,
                            isAppForeground = isAppForeground,
                            isFeedMuted = isFeedMuted,
                            currentUserId = currentUserId,
                            isAuthorProfileLoading = openingProfileUserId == post.author.id,
                            networkReconnectToken = networkReconnectToken,
                            isNetworkAvailable = isNetworkAvailable,
                            initialVideoPositionMs = videoPositionKey?.let { videoPositions[it] } ?: 0L,
                            onVideoPositionChanged = { positionMs ->
                                videoPositionKey?.let { videoPositions[it] = positionMs }
                            },
                            onOpenComments = { commentsPost = post },
                            onOpenUserProfile = { onOpenUserProfile(post.author.id) },
                            onOpenLive = { isLiveOpen = true },
                            onFeedMutedChange = { isFeedMuted = it },
                            onLike = {
                                if (canParticipate) {
                                    viewModel.onEvent(FeedUiEvent.ToggleLike(post.id))
                                } else {
                                    onAuthRequired()
                                }
                            },
                            onDelete = { postPendingDeletion = post },
                            onShare = {
                                val shareText = postShareText(post)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.feed_share_post)))
                            },
                            onReport = {
                                if (!post.isReportedByCurrentUser) {
                                    if (canParticipate) {
                                        viewModel.onEvent(FeedUiEvent.ReportPost(post.id))
                                        Toast.makeText(context, context.getString(R.string.feed_report_success), Toast.LENGTH_SHORT).show()
                                    } else {
                                        onAuthRequired()
                                    }
                                }
                            }
                        )
                    }
                }
                FeedPullRefreshIndicator(
                    pullDistancePx = pullRefreshDistancePx,
                    triggerDistancePx = pullRefreshTriggerPx,
                    isRefreshing = state.isRefreshing && pagerState.currentPage == 0,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            commentsPost?.let { post ->
                val currentPost = state.posts.firstOrNull { it.id == post.id } ?: post
                CommentsSheet(
                    post = currentPost,
                    canParticipate = canParticipate,
                    onAuthRequired = onAuthRequired,
                    onAddComment = { comment ->
                        viewModel.onEvent(FeedUiEvent.AddComment(currentPost.id, comment))
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

            postPendingDeletion?.let { post ->
                AlertDialog(
                    onDismissRequest = { postPendingDeletion = null },
                    title = { Text(stringResource(R.string.feed_delete_post_confirm_title)) },
                    text = { Text(stringResource(R.string.feed_delete_post_confirm_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingDeletedPostId = post.id
                                postPendingDeletion = null
                                viewModel.onEvent(FeedUiEvent.DeletePost(post.id))
                            }
                        ) {
                            Text(stringResource(R.string.feed_delete_post))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { postPendingDeletion = null }) {
                            Text(stringResource(R.string.common_cancel))
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
                CompactIconButton(onClick = onRefresh) {
                    CompactIcon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.common_refresh))
                }
            }
        }
    }
}

@Composable
private fun FeedPullRefreshIndicator(
    pullDistancePx: Float,
    triggerDistancePx: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = if (isRefreshing) {
        1f
    } else {
        (pullDistancePx / triggerDistancePx).coerceIn(0f, 1f)
    }
    if (progress <= 0f && !isRefreshing) return

    val template = quataTheme()
    val density = LocalDensity.current
    val indicatorOffset = with(density) {
        (-FeedPullRefreshIndicatorSize.toPx() + FeedPullRefreshIndicatorTravel.toPx() * progress).toDp()
    }
    val indicatorScale = 0.74f + 0.26f * progress

    Surface(
        modifier = modifier
            .offset(y = indicatorOffset)
            .graphicsLayer {
                alpha = if (isRefreshing) 1f else (0.28f + progress * 0.72f)
                rotationZ = if (isRefreshing) 0f else progress * 180f
                scaleX = indicatorScale
                scaleY = indicatorScale
            }
            .size(FeedPullRefreshIndicatorSize),
        shape = CircleShape,
        color = template.colors.surfaceRaised.copy(alpha = 0.96f),
        contentColor = template.colors.textPrimary,
        shadowElevation = 6.dp
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = QuataOrange,
                    strokeWidth = 2.5.dp
                )
            } else {
                CompactIcon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.common_refresh),
                    tint = if (progress >= 1f) QuataOrange else template.colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun LiveRankingDialog(
    posts: List<Post>,
    postRanks: Map<String, PostRankingInfo>,
    onDismiss: () -> Unit,
    onOpenPost: (Post) -> Unit
) {
    val template = quataTheme()
    val rankedPosts = remember(posts) {
        posts.sortedWith(postRankingComparator())
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = template.colors.surfaceRaised,
            contentColor = template.colors.textPrimary,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(580.dp)
                .border(1.dp, template.colors.divider, RoundedCornerShape(28.dp))
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
                            color = template.colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                    CompactIconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .border(1.dp, template.colors.divider, RoundedCornerShape(16.dp))
                    ) {
                        CompactIcon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = template.colors.textPrimary
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = template.colors.surfaceAlt,
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
                                color = template.colors.textSecondary
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
                            rank = postRanks[post.id]?.position ?: (rankedPosts.indexOf(post) + 1),
                            post = post,
                            template = template,
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
    template: QuataThemeTemplate,
    onOpenPost: () -> Unit
) {
    val borderColor = when (rank) {
        1 -> template.colors.live
        2 -> template.colors.divider
        3 -> QuataOrange.copy(alpha = 0.8f)
        else -> template.colors.divider.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .background(template.colors.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            color = template.colors.live,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.width(38.dp)
        )
        UserAvatar(post.author, modifier = Modifier.size(44.dp))
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
                color = template.colors.textSecondary,
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
                color = template.colors.surfaceAlt,
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
    postRankInfo: PostRankingInfo,
    isCurrentPage: Boolean,
    isAppForeground: Boolean,
    isFeedMuted: Boolean,
    currentUserId: String?,
    isAuthorProfileLoading: Boolean,
    networkReconnectToken: Long,
    isNetworkAvailable: Boolean,
    initialVideoPositionMs: Long,
    onVideoPositionChanged: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenUserProfile: () -> Unit,
    onOpenLive: () -> Unit,
    onFeedMutedChange: (Boolean) -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit
) {
    val isVideo = post.videoUrl != null
    val shortcodeContent = remember(post.text) { post.text.parsePostShortcodeContent() }
    val postMeta = remember(post.text) { post.text.extractPostMeta() }
    val displayText = shortcodeContent.cleanText
    val isTextOnly = post.videoUrl == null && post.imageUrl == null && displayText.isNotBlank()
    var isDescriptionExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val isVideoActive = isCurrentPage && isAppForeground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black)
    ) {
        ReelMedia(
            post = post,
            isActive = isVideoActive,
            isMuted = isFeedMuted,
            networkReconnectToken = networkReconnectToken,
            isNetworkAvailable = isNetworkAvailable,
            initialVideoPositionMs = initialVideoPositionMs,
            onVideoPositionChanged = onVideoPositionChanged,
            onMuteChange = onFeedMutedChange
        )
        ReelScrims()
        ReelTopChips(
            documentText = shortcodeContent.documentText,
            mediaBadgeText = when {
                post.videoUrl != null -> postMeta.mediaTitle.ifBlank { postMeta.imageLocation }
                post.imageUrl != null -> postMeta.imageLocation
                else -> ""
            },
            isVideo = isVideo
        )
        ReelActions(
            likes = post.likesCount,
            isLiked = post.isLikedByCurrentUser,
            isReported = post.isReportedByCurrentUser,
            comments = post.comments.size,
            postRank = postRankInfo.position,
            showRankLiveActions = !isLandscapeLayout,
            onLike = onLike,
            onOpenComments = onOpenComments,
            onOpenLive = onOpenLive,
            onShare = onShare,
            onReport = onReport,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp)
        )
        if (isLandscapeLayout) {
            ReelRankLiveActions(
                postRank = postRankInfo.position,
                onOpenLive = onOpenLive,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, bottom = 132.dp)
            )
        }
        if (post.author.id == currentUserId) {
            ReelActionButton(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.feed_delete_post),
                tint = Color.White,
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 18.dp, top = 18.dp)
            )
        }
        ReelAuthor(
            post = post,
            displayText = displayText,
            showDescription = (isVideo || post.imageUrl != null) && displayText.isNotBlank(),
            isDescriptionExpanded = isDescriptionExpanded,
            onToggleDescription = { isDescriptionExpanded = !isDescriptionExpanded },
            isProfileLoading = isAuthorProfileLoading,
            onOpenUserProfile = onOpenUserProfile,
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
    networkReconnectToken: Long,
    isNetworkAvailable: Boolean,
    initialVideoPositionMs: Long,
    onVideoPositionChanged: (Long) -> Unit,
    onMuteChange: (Boolean) -> Unit
) {
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    when {
        post.videoUrl != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(textCanvasBrush(post.videoUrl ?: post.id))
            ) {
                ReelVideo(
                    videoUrl = post.videoUrl,
                    isActive = isActive,
                    isMuted = isMuted,
                    networkReconnectToken = networkReconnectToken,
                    isNetworkAvailable = isNetworkAvailable,
                    initialPositionMs = initialVideoPositionMs,
                    onPositionChanged = onVideoPositionChanged,
                    onMuteChange = onMuteChange
                )
            }
        }
        post.imageUrl != null -> {
            val imageModel = rememberCachedRemoteImageRequest(post.imageUrl)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(textCanvasBrush(post.imageUrl ?: post.id)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = post.imageTitle(),
                    contentScale = if (isLandscapeLayout) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        post.text.parsePostShortcodeContent().cleanText.isNotBlank() -> TextOnlyReel(post = post)

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
    val meta = remember(post.text) { post.text.extractPostMeta() }
    val displayText = meta.cleanBody
    val seedText = remember(displayText) { displayText.cleanTextCanvasSeedBody() }
    val patternId = meta.textPattern.takeIf { it.isNotBlank() }
    val typography = remember(displayText) { textCanvasTypography(displayText) }
    var hasOverflow by remember(post.id, displayText) { mutableStateOf(false) }
    var isReaderOpen by rememberSaveable(post.id) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(textCanvasBrush(seedText, patternId))
            .padding(horizontal = TextOnlyReelActionRailSafePadding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = typography.fontSize,
                lineHeight = typography.lineHeight,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = typography.maxLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { hasOverflow = it.hasVisualOverflow }
            )
            if (hasOverflow) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.36f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { isReaderOpen = true }
                ) {
                    Text(
                        text = stringResource(R.string.feed_read_more),
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
    if (isReaderOpen) {
        Dialog(
            onDismissRequest = { isReaderOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(textCanvasBrush(seedText, patternId))
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 56.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = displayText,
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
                CompactIconButton(
                    onClick = { isReaderOpen = false },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    CompactIcon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

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
    var centerFeedbackIcon by remember { mutableStateOf<ImageVector?>(null) }
    var centerFeedbackTick by remember { mutableLongStateOf(0L) }
    var hasPlaybackError by remember(videoUrl) { mutableStateOf(false) }
    var isBuffering by remember(videoUrl) { mutableStateOf(false) }
    var hasStartedPlayback by remember(videoUrl) { mutableStateOf(initialPositionMs > 0L) }
    var retryCount by remember(videoUrl) { mutableStateOf(0) }
    var retrySignal by remember(videoUrl) { mutableLongStateOf(0L) }
    var playerGeneration by remember(videoUrl) { mutableLongStateOf(0L) }
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
            centerFeedbackIcon = null
        }
    }

    fun togglePlayback(showFeedback: Boolean) {
        if (hasPlaybackError) {
            retryCount = 0
            startPlayback()
            if (showFeedback) {
                centerFeedbackIcon = Icons.Filled.PlayArrow
                centerFeedbackTick = System.currentTimeMillis()
            }
            return
        }
        if (player.isPlaying) {
            player.pause()
            isPlaying = false
            isBuffering = false
            if (showFeedback) centerFeedbackIcon = Icons.Filled.Pause
        } else {
            startPlayback()
            if (showFeedback) centerFeedbackIcon = Icons.Filled.PlayArrow
        }
        if (showFeedback) centerFeedbackTick = System.currentTimeMillis()
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Transparent)
    ) {
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
                it.useController = false
                it.resizeMode = videoResizeMode
                if (it.player !== player) {
                    it.player = player
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
                CompactIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }
        }
        val showRebuffering = isBuffering && hasStartedPlayback
        if (showRebuffering && centerFeedbackIcon == null) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }
        VideoControls(
            isPlaying = isPlaying,
            isBuffering = showRebuffering,
            positionMs = positionMs,
            durationMs = durationMs,
            isMuted = isMuted,
            onPlayPause = { togglePlayback(showFeedback = false) },
            onSeek = { targetMs ->
                player.seekTo(targetMs)
                positionMs = targetMs
            },
            onToggleMute = { onMuteChange(!isMuted) },
            showMuteButton = !isLandscapeLayout,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 96.dp, bottom = 8.dp)
        )
    }
}

private fun ExoPlayer.setFeedAudioEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !enabled)
        .build()
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    isMuted: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    showMuteButton: Boolean,
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
        CompactIconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp)) {
            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                CompactIcon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) {
                        stringResource(R.string.feed_pause)
                    } else {
                        stringResource(R.string.feed_play)
                    },
                    tint = Color.White
                )
            }
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
        if (showMuteButton) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleMute),
                    contentAlignment = Alignment.Center
                ) {
                    CompactIcon(
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
                    0f to Color.Black.copy(alpha = 0.64f),
                    0.14f to Color.Black.copy(alpha = 0.42f),
                    0.34f to Color.Transparent,
                    0.58f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.68f)
                )
            )
    )
}

@Composable
private fun ReelTopChips(
    documentText: String?,
    mediaBadgeText: String,
    isVideo: Boolean
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val cleanMediaBadgeText = mediaBadgeText.trim()
        if (cleanMediaBadgeText.isNotBlank()) {
            Text(
                text = if (isVideo) {
                    "\uD83D\uDCDD $cleanMediaBadgeText"
                } else {
                    stringResource(R.string.feed_location_chip, cleanMediaBadgeText)
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        documentText?.let { text ->
            ReelChip(text = "\uD83D\uDCC4 $text")
        }
    }
}

@Composable
private fun ReelRoundChip(
    isMuted: Boolean,
    onClick: () -> Unit
) {
    val template = quataTheme()
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(template.colors.surface.copy(alpha = 0.74f))
            .border(1.dp, template.colors.live, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompactIcon(
            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = if (isMuted) {
                stringResource(R.string.feed_unmute)
            } else {
                stringResource(R.string.feed_mute)
            },
            tint = template.colors.live,
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
    val template = quataTheme()
    val borderColor = if (highlighted) template.colors.live else Color.White.copy(alpha = 0.22f)
    val textColor = if (highlighted) template.colors.live else Color.White

    Surface(
        color = if (highlighted) template.colors.surface.copy(alpha = 0.74f) else Color.White.copy(alpha = 0.12f),
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
    isLiked: Boolean,
    isReported: Boolean,
    comments: Int,
    postRank: Int,
    showRankLiveActions: Boolean,
    onLike: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenLive: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showRankLiveActions) {
            ReelRankLiveActions(
                postRank = postRank,
                onOpenLive = onOpenLive
            )
        }
        ReelActionButton(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(R.string.feed_like),
            count = likes.toString(),
            tint = if (isLiked) Color(0xFFFF7EA8) else Color.White,
            onClick = onLike
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
            tint = if (isReported) QuataOrange else Color.White,
            onClick = onReport
        )
    }
}

@Composable
private fun ReelRankLiveActions(
    postRank: Int,
    onOpenLive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ReelTextActionButton(
            text = "\uD83D\uDD25",
            contentDescription = stringResource(R.string.feed_rank),
            count = postRank.toString(),
            onClick = onOpenLive
        )
        ReelTextActionButton(
            text = stringResource(R.string.common_live),
            contentDescription = stringResource(R.string.common_live),
            onClick = onOpenLive
        )
    }
}

@Composable
private fun ReelTextActionButton(
    text: String,
    contentDescription: String,
    count: String? = null,
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .semantics { this.contentDescription = contentDescription }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = text,
                    color = tint,
                    fontSize = if (text.length <= 2) 19.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = if (text.length <= 2) 20.sp else 12.sp,
                    modifier = Modifier.padding(horizontal = 5.dp)
                )
                if (count != null) {
                    Text(
                        text = count,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 10.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    post: Post,
    canParticipate: Boolean,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var draft by rememberSaveable(post.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var replyTarget by remember { mutableStateOf<PostComment?>(null) }
    var isEmojiPickerVisible by rememberSaveable(post.id) { mutableStateOf(false) }
    val emojiDismissState = rememberCommunityEmojiPanelDismissState {
        isEmojiPickerVisible = false
    }
    var shouldScrollToCommentsEnd by remember { mutableStateOf(true) }
    val commentsListState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.PartiallyExpanded }
    )
    val template = quataTheme()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val emojiGridMaxHeight = if (isImeVisible) 168.dp else 220.dp
    val comments = post.comments
    val translatorModeController = LocalQuataTranslatorModeController.current
    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    fun setEmojiPickerVisible(visible: Boolean) {
        isEmojiPickerVisible = visible
        if (visible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
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

    if (isLandscapeLayout) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                decorFitsSystemWindows = false
            )
        ) {
            ConfigureCommentsSheetSystemBars(template, fullscreen = true)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 72.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                val panelInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(onDismiss) {
                            detectTapGestures { onDismiss() }
                        }
                )
                Surface(
                    color = template.colors.surfaceRaised,
                    contentColor = template.colors.textPrimary,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .fillMaxHeight(0.90f)
                        .border(1.dp, template.colors.divider.copy(alpha = 0.72f), RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = panelInteractionSource,
                            indication = null,
                            onClick = {}
                        )
                ) {
                    LandscapeCommentsPanel(
                        post = post,
                        comments = comments,
                        commentsListState = commentsListState,
                        draft = draft,
                        onDraftChange = { draft = it },
                        replyTarget = replyTarget,
                        onReplyTargetChange = { replyTarget = it },
                        isEmojiPickerVisible = isEmojiPickerVisible,
                        onEmojiPickerVisibleChange = ::setEmojiPickerVisible,
                        emojiDismissState = emojiDismissState,
                        emojiGridMaxHeight = emojiGridMaxHeight,
                        canParticipate = canParticipate,
                        onAuthRequired = onAuthRequired,
                        onAddComment = onAddComment,
                        onCommentAdded = { shouldScrollToCommentsEnd = true },
                        onTranslatorClick = { view ->
                            translatorModeController.activate(view, QuataTranslatorOverlaySource.Comments)
                        },
                        onDismiss = onDismiss,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = template.colors.surfaceRaised,
        contentColor = template.colors.textPrimary,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        ConfigureCommentsSheetSystemBars(template)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            Spacer(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(template.colors.background)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                .dismissCommunityEmojiPanelOnOutsideTap(
                    isVisible = isEmojiPickerVisible,
                    state = emojiDismissState
                )
                .padding(start = 20.dp, end = 20.dp, bottom = 48.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.comments_title),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = template.colors.textSecondary
                )
                Spacer(Modifier.width(10.dp))
                Text("\uD83D\uDCAC", fontSize = 16.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = comments.size.toString(),
                    color = template.colors.textSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                FangTranslatorIconButton(
                    onClick = { view ->
                        translatorModeController.activate(view, QuataTranslatorOverlaySource.Comments)
                    }
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
                CommunityEmojiPanel(
                    onEmojiClick = { emoji ->
                        draft = draft.insertAtSelection(emoji)
                    },
                    gridMaxHeight = emojiGridMaxHeight,
                    modifier = Modifier.trackCommunityEmojiPanelBounds(emojiDismissState)
                )
                Spacer(Modifier.height(18.dp))
            }
            replyTarget?.let { target ->
                ReplyTargetBanner(
                    comment = target,
                    onClear = { replyTarget = null }
                )
                Spacer(Modifier.height(14.dp))
            }
            Row(
                modifier = Modifier.requiredHeightIn(min = 82.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(stringResource(R.string.comments_placeholder)) },
                    leadingIcon = {
                        CompactIconButton(
                            onClick = { setEmojiPickerVisible(!isEmojiPickerVisible) },
                            modifier = Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState)
                        ) {
                            CompactIcon(
                                imageVector = Icons.Filled.InsertEmoticon,
                                contentDescription = stringResource(R.string.comments_show_emojis),
                                tint = Color(0xFFFFC55C)
                            )
                        }
                    },
                    trailingIcon = {
                        CompactIconButton(
                            enabled = draft.text.isNotBlank(),
                            onClick = {
                                if (canParticipate) {
                                    onAddComment(
                                        PostComment(
                                            id = "local_${post.id}_${System.currentTimeMillis()}",
                                            authorName = context.getString(R.string.comments_you),
                                            message = draft.text.trim(),
                                            timestamp = nowCommentTimestamp(),
                                            replyToAuthorName = replyTarget?.authorName,
                                            replyToMessage = replyTarget?.message,
                                            replyToCommentId = replyTarget?.id
                                        )
                                    )
                                    shouldScrollToCommentsEnd = true
                                    draft = TextFieldValue("")
                                    replyTarget = null
                                    isEmojiPickerVisible = false
                                } else {
                                    onAuthRequired()
                                }
                            }
                        ) {
                            CompactIcon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.comments_send)
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .requiredHeightIn(min = 68.dp)
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && isEmojiPickerVisible) {
                                isEmojiPickerVisible = false
                            }
                        },
                    singleLine = true
                )
            }
        }
    }
}
}

@Composable
private fun LandscapeCommentsPanel(
    post: Post,
    comments: List<PostComment>,
    commentsListState: LazyListState,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    replyTarget: PostComment?,
    onReplyTargetChange: (PostComment?) -> Unit,
    isEmojiPickerVisible: Boolean,
    onEmojiPickerVisibleChange: (Boolean) -> Unit,
    emojiDismissState: CommunityEmojiPanelDismissState,
    emojiGridMaxHeight: Dp,
    canParticipate: Boolean,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onCommentAdded: () -> Unit,
    onTranslatorClick: (View) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val template = quataTheme()
    Box(
        modifier = modifier
            .dismissCommunityEmojiPanelOnOutsideTap(
                isVisible = isEmojiPickerVisible,
                state = emojiDismissState
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 18.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.comments_title),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = template.colors.textSecondary
            )
            Spacer(Modifier.width(10.dp))
            Text("\uD83D\uDCAC", fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = comments.size.toString(),
                color = template.colors.textSecondary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            FangTranslatorIconButton(onClick = onTranslatorClick)
            Spacer(Modifier.width(8.dp))
            CompactIconButton(onClick = onDismiss) {
                CompactIcon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = template.colors.textSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = commentsListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 140.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(comments) { comment ->
                CommentRow(
                    comment = comment,
                    onReply = { onReplyTargetChange(comment) }
                )
            }
            item(key = "comments-end") {
                Spacer(Modifier.height(12.dp))
            }
        }
        replyTarget?.let { target ->
            Spacer(Modifier.height(10.dp))
            ReplyTargetBanner(
                comment = target,
                onClear = { onReplyTargetChange(null) }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.requiredHeightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = { Text(stringResource(R.string.comments_placeholder)) },
                leadingIcon = {
                    CompactIconButton(
                        onClick = { onEmojiPickerVisibleChange(!isEmojiPickerVisible) },
                        modifier = Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState)
                    ) {
                        CompactIcon(
                            imageVector = Icons.Filled.InsertEmoticon,
                            contentDescription = stringResource(R.string.comments_show_emojis),
                            tint = Color(0xFFFFC55C)
                        )
                    }
                },
                trailingIcon = {
                    CompactIconButton(
                        enabled = draft.text.isNotBlank(),
                        onClick = {
                            if (canParticipate) {
                                onAddComment(
                                    PostComment(
                                        id = "local_${post.id}_${System.currentTimeMillis()}",
                                        authorName = context.getString(R.string.comments_you),
                                        message = draft.text.trim(),
                                        timestamp = nowCommentTimestamp(),
                                        replyToAuthorName = replyTarget?.authorName,
                                        replyToMessage = replyTarget?.message,
                                        replyToCommentId = replyTarget?.id
                                    )
                                )
                                onCommentAdded()
                                onDraftChange(TextFieldValue(""))
                                onReplyTargetChange(null)
                                onEmojiPickerVisibleChange(false)
                            } else {
                                onAuthRequired()
                            }
                        }
                    ) {
                        CompactIcon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.comments_send)
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .requiredHeightIn(min = 58.dp)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && isEmojiPickerVisible) {
                            onEmojiPickerVisibleChange(false)
                        }
                    },
                singleLine = true
            )
        }
        }
        if (isEmojiPickerVisible) {
            CommunityEmojiPanel(
                onEmojiClick = { emoji ->
                    onDraftChange(draft.insertAtSelection(emoji))
                },
                gridMaxHeight = emojiGridMaxHeight,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 84.dp, start = 24.dp)
                    .fillMaxWidth(0.62f)
                    .trackCommunityEmojiPanelBounds(emojiDismissState)
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun ConfigureCommentsSheetSystemBars(
    template: QuataThemeTemplate,
    fullscreen: Boolean = false
) {
    val view = LocalView.current
    DisposableEffect(view, template.id) {
        val window = view.findDialogWindow()
        if (window == null) {
            return@DisposableEffect onDispose {}
        }
        val originalContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val originalLightNavigationBars = controller.isAppearanceLightNavigationBars
        val originalAttributes = window.attributes
        val originalGravity = originalAttributes.gravity
        val originalX = originalAttributes.x
        val originalY = originalAttributes.y
        val originalWidth = originalAttributes.width
        val originalHeight = originalAttributes.height
        val originalFlags = originalAttributes.flags
        val originalSystemUiVisibility = window.decorView.systemUiVisibility
        val originalCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            originalAttributes.layoutInDisplayCutoutMode
        } else {
            null
        }
        val fullscreenLayout = {
            val displaySize = Point()
            @Suppress("DEPRECATION")
            window.windowManager.defaultDisplay.getRealSize(displaySize)
            val targetWidth = displaySize.x.coerceAtLeast(1)
            val targetHeight = displaySize.y.coerceAtLeast(1)
            val attributes = window.attributes
            attributes.width = targetWidth
            attributes.height = targetHeight
            attributes.gravity = Gravity.TOP or Gravity.START
            attributes.x = 0
            attributes.y = 0
            attributes.flags = attributes.flags or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            window.attributes = attributes
            window.setLayout(targetWidth, targetHeight)
            window.decorView.setPadding(0, 0, 0, 0)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        controller.isAppearanceLightNavigationBars = template.resolvedTheme == QuataResolvedTheme.Light
        if (fullscreen) {
            fullscreenLayout()
            window.decorView.post { fullscreenLayout() }
        }

        onDispose {
            if (fullscreen) {
                val attributes = window.attributes
                attributes.gravity = originalGravity
                attributes.x = originalX
                attributes.y = originalY
                attributes.width = originalWidth
                attributes.height = originalHeight
                attributes.flags = originalFlags
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && originalCutoutMode != null) {
                    attributes.layoutInDisplayCutoutMode = originalCutoutMode
                }
                window.attributes = attributes
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = originalSystemUiVisibility
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && originalContrastEnforced != null) {
                window.isNavigationBarContrastEnforced = originalContrastEnforced
            }
            controller.isAppearanceLightNavigationBars = originalLightNavigationBars
        }
    }
}

private tailrec fun View.findDialogWindow(): Window? {
    val parentView = parent
    return when (parentView) {
        is DialogWindowProvider -> parentView.window
        is View -> parentView.findDialogWindow()
        else -> null
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
    val template = quataTheme()
    Surface(
        color = template.colors.accent.copy(alpha = 0.08f),
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.accent.copy(alpha = 0.34f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.comments_replying_to, comment.authorName),
                    color = template.colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = comment.message,
                    color = template.colors.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            CompactIconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(template.colors.surfaceAlt)
            ) {
                CompactIcon(Icons.Filled.Close, contentDescription = stringResource(R.string.comments_cancel_reply))
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: PostComment,
    onReply: () -> Unit
) {
    val template = quataTheme()
    val translatorReplyText = comment.replyToAuthorName?.let { author ->
        stringResource(R.string.comments_reply_to, author)
    }
    val translatorDisplayText = remember(comment, translatorReplyText) {
        comment.translatorDisplayText(translatorReplyText)
    }
    Surface(
        color = template.colors.surface,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .quataTranslatableText(
                id = "feed-comment:${comment.id}",
                text = comment.message,
                displayText = translatorDisplayText
            )
            .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
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
                        .background(template.colors.accent)
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = comment.authorName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = template.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatCommentTimestamp(comment.timestamp),
                        color = template.colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
                comment.replyToAuthorName?.let { author ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.comments_reply_to, author),
                        color = template.colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    comment.replyToMessage?.takeIf { it.isNotBlank() }?.let { quoted ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = quoted,
                            color = template.colors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = comment.message,
                    color = template.colors.textPrimary,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onReply,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.comments_reply_button),
                        color = template.colors.accent,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

private fun PostComment.translatorDisplayText(replyText: String?): String =
    buildString {
        append(authorName)
        val timestampText = formatCommentTimestamp(timestamp)
        if (timestampText.isNotBlank()) {
            append(" · ")
            append(timestampText)
        }
        replyText?.let { reply ->
            append('\n')
            append(reply)
        }
        replyToMessage?.takeIf { it.isNotBlank() }?.let { quoted ->
            append('\n')
            append(quoted)
        }
        if (message.isNotBlank()) {
            append('\n')
            append(message)
        }
    }

private fun nowCommentTimestamp(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("d/M/yyyy, H:mm:ss"))

private fun formatCommentTimestamp(value: String): String {
    val normalized = value.trim()
    if (normalized.isBlank()) return ""
    val parsed = parseAbsoluteDateTime(normalized) ?: return normalized
    return parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}

private data class PostRankingInfo(
    val position: Int,
    val likes: Int
)

private data class PostPublishedAtInfo(
    val publishedAt: LocalDateTime
)

private val FeedPullRefreshTriggerDistance = 96.dp
private val FeedPullRefreshMaxDistance = 148.dp
private val FeedPullRefreshIndicatorSize = 44.dp
private val FeedPullRefreshIndicatorTravel = 72.dp
private val TextOnlyReelActionRailSafePadding = 92.dp

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

@Composable
private fun ReelActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    count: String? = null,
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CompactIcon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(25.dp)
                )
                if (count != null) {
                    Text(
                        text = count,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 11.sp
                    )
                }
            }
        }
    }
}

private fun postShareText(post: Post): String = quataPostUrl(post.id)

@Composable
private fun ReelAuthor(
    post: Post,
    displayText: String,
    showDescription: Boolean,
    isDescriptionExpanded: Boolean,
    onToggleDescription: () -> Unit,
    isProfileLoading: Boolean,
    onOpenUserProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showDescription) {
            Text(
                text = displayText,
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
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClickableProfileAvatar(
                name = post.author.displayName,
                avatarUrl = post.author.avatarUrl,
                isLoading = isProfileLoading,
                onClick = onOpenUserProfile,
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
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                post.author.neighborhood.takeIf { it.isNotBlank() }?.let { neighborhood ->
                    Text(
                        text = neighborhood,
                        color = QuataOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun Post.imageTitle(): String =
    placeName?.takeIf { it.isNotBlank() } ?: rankingLabel.takeIf { it.isNotBlank() } ?: "Qüata"
