@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.quata.feature.feed.presentation

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Add
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
import com.quata.core.ui.components.QuataCommentsPanel
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanel
import com.quata.core.ui.components.QuataFeedActionRail
import com.quata.core.ui.components.QuataFeedOverflowActionButton
import com.quata.core.ui.components.QuataFeedPullRefreshIndicator
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
import com.quata.core.ui.components.applyQuataVideoPlaybackTransform
import com.quata.core.ui.components.findQuataTextureView
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.readQuataVideoRotation
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onCreatePost: () -> Unit = {},
    onReportComment: (String) -> Unit = {},
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
    val canModerateAll = state.currentUser?.isAdmin == true
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
            var retainedVisiblePostId by rememberSaveable { mutableStateOf<String?>(null) }
            var hasAppliedRetainedPost by remember { mutableStateOf(retainedVisiblePostId == null) }
            var lastHandledFeedResetToken by rememberSaveable { mutableStateOf(feedResetToken) }
            val canPullRefresh =
                pagerState.currentPage == 0 &&
                    !state.isRefreshing &&
                    commentsPost == null &&
                    !isLiveOpen
            val pullRefreshState = rememberQuataFeedPullRefreshState(
                enabled = canPullRefresh,
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) }
            )

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
                    retainedVisiblePostId = targetId
                    hasAppliedRetainedPost = true
                    handledFocusedPostId = targetId
                    onFocusedPostHandled()
                }
            }

            LaunchedEffect(retainedVisiblePostId, state.posts, focusedPostId) {
                val targetId = retainedVisiblePostId
                if (
                    !hasAppliedRetainedPost &&
                    focusedPostId == null &&
                    targetId != null &&
                    state.posts.isNotEmpty()
                ) {
                    val targetIndex = state.posts.indexOfFirst { it.id == targetId }
                    if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                        pagerState.scrollToPage(targetIndex)
                    }
                    hasAppliedRetainedPost = true
                }
            }

            LaunchedEffect(feedResetToken, state.posts.size) {
                if (
                    feedResetToken != lastHandledFeedResetToken &&
                    focusedPostId == null &&
                    state.posts.isNotEmpty()
                ) {
                    pagerState.scrollToPage(0)
                    retainedVisiblePostId = state.posts.firstOrNull()?.id
                    hasAppliedRetainedPost = true
                    lastHandledFeedResetToken = feedResetToken
                }
            }

            val visiblePostId = state.posts.getOrNull(pagerState.currentPage)?.id
            val nextPostId = state.posts.getOrNull(pagerState.currentPage + 1)?.id
            LaunchedEffect(visiblePostId, nextPostId) {
                if (hasAppliedRetainedPost && visiblePostId != null) {
                    retainedVisiblePostId = visiblePostId
                }
                visiblePostId?.let { postId ->
                    viewModel.onEvent(FeedUiEvent.PostDisplayed(postId, nextPostId))
                }
            }

            LaunchedEffect(
                pagerState.currentPage,
                state.posts.size,
                state.hasMoreOlderPosts,
                state.isLoadingOlder
            ) {
                val shouldLoadOlder =
                    state.posts.isNotEmpty() &&
                        state.hasMoreOlderPosts &&
                        !state.isLoadingOlder &&
                        pagerState.currentPage >= state.posts.lastIndex - FeedOlderPostsPrefetchDistance
                if (shouldLoadOlder) {
                    viewModel.onEvent(FeedUiEvent.LoadOlderPage)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
                    .background(Color.Black)
            ) {
                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val post = state.posts[page]
                    val videoPositionKey = post.videoUrl
                    val canDeletePost = post.author.id == currentUserId || canModerateAll
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
                            canDelete = canDeletePost,
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
                            },
                            onCreatePost = {
                                if (canParticipate) onCreatePost() else onAuthRequired()
                            }
                        )
                    }
                }
                QuataFeedPullRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = state.isRefreshing && pagerState.currentPage == 0,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            commentsPost?.let { post ->
                val currentPost = state.posts.firstOrNull { it.id == post.id } ?: post
                QuataCommentsPanel(
                    postId = currentPost.id,
                    comments = currentPost.comments,
                    canParticipate = canParticipate,
                    onAuthRequired = onAuthRequired,
                    onAddComment = { comment ->
                        viewModel.onEvent(FeedUiEvent.AddComment(currentPost.id, comment))
                    },
                    onReportComment = { comment ->
                        if (canParticipate) onReportComment(comment.id) else onAuthRequired()
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
    QuataScreen(padding, applyLandscapeSafeDrawing = false) {
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
private fun LiveRankingDialog(
    posts: List<Post>,
    postRanks: Map<String, PostRankingInfo>,
    onDismiss: () -> Unit,
    onOpenPost: (Post) -> Unit
) {
    val rankedPosts = remember(posts) {
        posts.sortedWith(postRankingComparator())
    }
    val postsById = remember(posts) { posts.associateBy { it.id } }
    QuataLiveRankingPanel(
        items = rankedPosts.mapIndexed { index, post ->
            QuataLiveRankingItem(
                id = post.id,
                profileId = post.author.id,
                rank = postRanks[post.id]?.position ?: (index + 1),
                title = post.author.displayName,
                subtitle = postTypeLabel(post),
                avatarName = post.author.displayName,
                avatarUrl = post.author.avatarUrl,
                isOfficial = post.author.isOfficial,
                likesCount = post.likesCount
            )
        },
        onDismiss = onDismiss,
        onOpenItem = { postId -> postsById[postId]?.let(onOpenPost) }
    )
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
    canDelete: Boolean,
    initialVideoPositionMs: Long,
    onVideoPositionChanged: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenUserProfile: () -> Unit,
    onOpenLive: () -> Unit,
    onFeedMutedChange: (Boolean) -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onCreatePost: () -> Unit
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
        val mediaBadgeText = when {
            post.videoUrl != null -> postMeta.mediaTitle.ifBlank { postMeta.imageLocation }
            post.imageUrl != null -> postMeta.imageLocation
            else -> ""
        }
        val hasTopOverlayText = mediaBadgeText.isNotBlank() || !shortcodeContent.documentText.isNullOrBlank()
        ReelScrims(showTopScrim = !isVideo || hasTopOverlayText)
        ReelTopChips(
            documentText = shortcodeContent.documentText,
            mediaBadgeText = mediaBadgeText,
            isVideo = isVideo
        )
        QuataFeedActionRail(
            likes = post.likesCount,
            isLiked = post.isLikedByCurrentUser,
            comments = post.comments.size,
            postRank = postRankInfo.position,
            isLandscape = isLandscapeLayout,
            likeLabel = stringResource(R.string.feed_like),
            commentsLabel = stringResource(R.string.feed_comments),
            shareLabel = stringResource(R.string.feed_share),
            rankLabel = stringResource(R.string.feed_rank),
            liveLabel = stringResource(R.string.common_live),
            publishLabel = stringResource(R.string.nav_publish),
            isReported = post.isReportedByCurrentUser,
            reportLabel = stringResource(R.string.feed_report),
            deleteLabel = stringResource(R.string.feed_delete_post),
            showReport = true,
            showDelete = canDelete,
            showPublish = true,
            onLike = onLike,
            onOpenComments = onOpenComments,
            onOpenLive = onOpenLive,
            onShare = onShare,
            onReport = onReport,
            onDelete = onDelete,
            onPublish = onCreatePost,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp)
        )
        if (isLandscapeLayout) {
            QuataFeedOverflowActionButton(
                postRank = postRankInfo.position,
                rankLabel = stringResource(R.string.feed_rank),
                liveLabel = stringResource(R.string.common_live),
                reportLabel = stringResource(R.string.feed_report),
                showReport = true,
                onOpenLive = onOpenLive,
                onReport = onReport,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = if (isVideo) 148.dp else 86.dp)
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
private fun ReelScrims(showTopScrim: Boolean) {
    val stops = if (showTopScrim) {
        arrayOf(
            0f to Color.Black.copy(alpha = 0.64f),
            0.14f to Color.Black.copy(alpha = 0.42f),
            0.34f to Color.Transparent,
            0.58f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.68f)
        )
    } else {
        arrayOf(
            0f to Color.Transparent,
            0.58f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.68f)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(*stops)
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

private data class PostRankingInfo(
    val position: Int,
    val likes: Int
)

private data class PostPublishedAtInfo(
    val publishedAt: LocalDateTime
)

private const val FeedOlderPostsPrefetchDistance = 8
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
                isOfficial = post.author.isOfficial,
                profileId = post.author.id,
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
