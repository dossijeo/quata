package com.quata.feature.official.presentation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.PostComment
import com.quata.core.navigation.quataOfficialPostUrl
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AttachmentViewerDialog
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.QuataCommentsPanel
import com.quata.core.ui.components.QuataFeedActionRail
import com.quata.core.ui.components.QuataFeedOverflowActionButton
import com.quata.core.ui.components.QuataFeedPullRefreshIndicator
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanel
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataStandardFloatingPanel
import com.quata.core.ui.components.VideoAttachmentThumbnail
import com.quata.core.ui.components.rememberQuataFeedPullRefreshState
import com.quata.core.ui.richtext.QuataRichTextEditorBox
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import kotlinx.coroutines.launch

@Composable
fun OfficialFeedScreen(
    padding: PaddingValues,
    repository: OfficialRepository,
    currentUserId: String?,
    focusedPostId: String? = null,
    onFocusedPostHandled: () -> Unit = {},
    onAuthRequired: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: (() -> Unit)? = null,
    onReportComment: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OfficialFeedViewModel = viewModel(factory = OfficialFeedViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var readMorePost by remember { mutableStateOf<OfficialPostItem?>(null) }
    var commentsPost by remember { mutableStateOf<OfficialPostItem?>(null) }
    var isLiveOpen by remember { mutableStateOf(false) }
    var mediaPost by remember { mutableStateOf<OfficialPostItem?>(null) }
    var postPendingDelete by remember { mutableStateOf<OfficialPostItem?>(null) }
    var handledFocusedPostId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(currentUserId) {
        viewModel.refreshCurrentUser()
    }

    LaunchedEffect(state.message) {
        if (state.message == OfficialFeedMessages.PostDeleted) {
            Toast.makeText(context, context.getString(R.string.feed_delete_post_success), Toast.LENGTH_SHORT).show()
            viewModel.onEvent(OfficialFeedUiEvent.ClearMessage)
        }
    }

    val canPublish = state.currentUser?.isOfficial == true
    val canModerateAll = state.currentUser?.isAdmin == true
    val postRanks = remember(state.posts) { calculateOfficialPostRankingMap(state.posts) }
    val pagerState = rememberPagerState(pageCount = { state.posts.size.coerceAtLeast(1) })
    var retainedVisiblePostId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAppliedRetainedPost by remember { mutableStateOf(retainedVisiblePostId == null) }
    val canPullRefresh =
        pagerState.currentPage == 0 &&
            !state.isRefreshing &&
            commentsPost == null &&
            readMorePost == null &&
            !isLiveOpen
    val pullRefreshState = rememberQuataFeedPullRefreshState(
        enabled = canPullRefresh,
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.onEvent(OfficialFeedUiEvent.Refresh) }
    )
    fun requestCreateOfficialPost() {
        if (currentUserId == null) {
            onAuthRequired()
        } else {
            onCreateOfficialPost?.invoke()
        }
    }

    LaunchedEffect(focusedPostId) {
        val targetId = focusedPostId ?: return@LaunchedEffect
        if (targetId != handledFocusedPostId && state.posts.none { it.id == targetId }) {
            viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(targetId))
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

    val visiblePostId = state.posts.getOrNull(pagerState.currentPage)?.id
    LaunchedEffect(visiblePostId) {
        if (hasAppliedRetainedPost && visiblePostId != null) {
            retainedVisiblePostId = visiblePostId
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
                pagerState.currentPage >= state.posts.lastIndex - OfficialOlderPostsPrefetchDistance
        if (shouldLoadOlder) {
            viewModel.onEvent(OfficialFeedUiEvent.LoadOlderPage)
        }
    }

    QuataScreen(padding = padding, applyLandscapeSafeDrawing = false) {
        Box(
            modifier
                .fillMaxSize()
                .nestedScroll(pullRefreshState.nestedScrollConnection)
        ) {
            when {
                state.isLoading && state.posts.isEmpty() -> {
                    OfficialLoadingPage(
                        canPublish = canPublish,
                        onCreate = { requestCreateOfficialPost() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.posts.isEmpty() -> {
                    OfficialEmptyPage(
                        canPublish = canPublish,
                        onCreate = { requestCreateOfficialPost() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    VerticalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val post = state.posts[page]
                        OfficialPostPage(
                            post = post,
                            rank = postRanks[post.id]?.position ?: (page + 1),
                            canPublish = canPublish,
                            canModerate = canModerateAll || post.author.id == currentUserId,
                            onCreate = { requestCreateOfficialPost() },
                            onOpenAuthor = { onOpenUserProfile(post.author.id) },
                            onReadMore = { readMorePost = post },
                            onOpenMedia = { mediaPost = post },
                            onOpenLive = { isLiveOpen = true },
                            onLike = {
                                if (currentUserId == null) onAuthRequired() else viewModel.onEvent(OfficialFeedUiEvent.ToggleLike(post.id))
                            },
                            onComment = { commentsPost = post },
                            onShare = { shareOfficialPost(context, post) },
                            onDelete = { postPendingDelete = post },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            QuataFeedPullRefreshIndicator(
                state = pullRefreshState,
                isRefreshing = state.isRefreshing && pagerState.currentPage == 0,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(4f)
            )
            if (state.isLoadingOlder) {
                CircularProgressIndicator(
                    color = QuataOrange.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .size(22.dp)
                )
            }
        }
    }

    readMorePost?.let { post ->
        OfficialPostDetailPanel(
            post = post,
            onDismiss = { readMorePost = null }
        )
    }

    commentsPost?.let { post ->
        val currentPost = state.posts.firstOrNull { it.id == post.id } ?: post
        QuataCommentsPanel(
            postId = currentPost.id,
            comments = currentPost.comments,
            canParticipate = currentUserId != null,
            onAuthRequired = onAuthRequired,
            onAddComment = { comment ->
                if (currentUserId == null) {
                    onAuthRequired()
                } else {
                    viewModel.onEvent(
                        OfficialFeedUiEvent.AddComment(
                            currentPost.id,
                            comment
                        )
                    )
                }
            },
            onReportComment = { comment ->
                if (currentUserId != null) onReportComment(comment.id) else onAuthRequired()
            },
            onDismiss = { commentsPost = null }
        )
    }

    if (isLiveOpen) {
        val rankedOfficialPosts = remember(state.posts) {
            state.posts.sortedWith(compareByDescending<OfficialPostItem> { it.likesCount }.thenByDescending { it.createdAt })
        }
        val postsById = remember(state.posts) { state.posts.associateBy { it.id } }
        QuataLiveRankingPanel(
            items = rankedOfficialPosts.mapIndexed { index, post ->
                QuataLiveRankingItem(
                    id = post.id,
                    rank = postRanks[post.id]?.position ?: (index + 1),
                    title = post.title,
                    subtitle = post.author.displayName,
                    avatarName = post.author.displayName,
                    avatarUrl = post.author.avatarUrl,
                    isOfficial = true,
                    likesCount = post.likesCount
                )
            },
            onDismiss = { isLiveOpen = false },
            onOpenItem = { postId ->
                postsById[postId]?.let { post ->
                    val index = state.posts.indexOfFirst { it.id == post.id }
                    if (index >= 0) {
                        isLiveOpen = false
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                }
            }
        )
    }

    mediaPost?.let { post ->
        OfficialMediaViewerDialog(post = post, onDismiss = { mediaPost = null })
    }

    postPendingDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { postPendingDelete = null },
            title = { Text(stringResource(R.string.official_delete_confirm_title)) },
            text = { Text(stringResource(R.string.official_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(OfficialFeedUiEvent.DeletePost(post.id))
                        postPendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { postPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun OfficialPostPage(
    post: OfficialPostItem,
    rank: Int,
    canPublish: Boolean,
    canModerate: Boolean,
    onCreate: () -> Unit,
    onOpenAuthor: () -> Unit,
    onReadMore: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenLive: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 14.dp)
    ) {
        OfficialPostCard(
            post = post,
            rank = rank,
            canPublish = canPublish,
            canModerate = canModerate,
            onCreate = onCreate,
            onOpenAuthor = onOpenAuthor,
            onReadMore = onReadMore,
            onOpenMedia = onOpenMedia,
            onOpenLive = onOpenLive,
            onLike = onLike,
            onComment = onComment,
            onShare = onShare,
            onDelete = onDelete,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 10.dp)
        )
    }
}

@Composable
private fun OfficialLoadingPage(
    canPublish: Boolean,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape

    Box(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        OfficialPostSkeleton(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 10.dp)
        )
        if (canPublish) {
            OfficialCreateActionButton(
                onClick = onCreate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun OfficialEmptyPage(
    canPublish: Boolean,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = rememberQuataWindowLayoutInfo().isLandscape

    Box(
        modifier = modifier.padding(horizontal = 14.dp)
    ) {
        OfficialEmptyState(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 10.dp)
        )
        if (canPublish) {
            OfficialCreateActionButton(
                onClick = onCreate,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun OfficialCreateActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = QuataOrange,
        contentColor = Color.White,
        shape = CircleShape,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.88f)),
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CompactIcon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.official_create),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
internal fun OfficialPostCard(
    post: OfficialPostItem,
    rank: Int,
    canPublish: Boolean,
    canModerate: Boolean,
    onCreate: () -> Unit,
    onOpenAuthor: () -> Unit,
    onReadMore: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenLive: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    forcePortraitLayout: Boolean = false
) {
    val template = quataTheme()
    val windowInfo = rememberQuataWindowLayoutInfo()
    val isLandscape = !forcePortraitLayout && windowInfo.isLandscape
    Card(
        colors = CardDefaults.cardColors(containerColor = template.colors.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            template.colors.surface.copy(alpha = 0.98f),
                            template.colors.surfaceRaised.copy(alpha = 0.78f)
                        )
                    )
                )
        ) {
            if (isLandscape) {
                OfficialPostLandscapeContent(
                    post = post,
                    onOpenAuthor = onOpenAuthor,
                    onReadMore = onReadMore,
                    onOpenMedia = onOpenMedia,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, top = 18.dp, end = 76.dp, bottom = 18.dp)
                )
                OfficialPostActionRail(
                    post = post,
                    rank = rank,
                    isLandscape = true,
                    canPublish = canPublish,
                    canModerate = canModerate,
                    onCreate = onCreate,
                    onOpenLive = onOpenLive,
                    onLike = onLike,
                    onComment = onComment,
                    onShare = onShare,
                    onDelete = onDelete,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 16.dp)
                )
                OfficialTypePill(
                    post.type,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 28.dp, end = 14.dp)
                )
                QuataFeedOverflowActionButton(
                    postRank = rank,
                    rankLabel = stringResource(R.string.feed_rank),
                    liveLabel = stringResource(R.string.common_live),
                    reportLabel = null,
                    showReport = false,
                    onOpenLive = onOpenLive,
                    onReport = {},
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 28.dp, bottom = 28.dp)
                )
            } else {
                OfficialPostPortraitContent(
                    post = post,
                    onOpenAuthor = onOpenAuthor,
                    onReadMore = onReadMore,
                    onOpenMedia = onOpenMedia,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, top = 20.dp, end = 76.dp, bottom = 18.dp)
                )
                OfficialPostActionRail(
                    post = post,
                    rank = rank,
                    isLandscape = false,
                    canPublish = canPublish,
                    canModerate = canModerate,
                    onCreate = onCreate,
                    onOpenLive = onOpenLive,
                    onLike = onLike,
                    onComment = onComment,
                    onShare = onShare,
                    onDelete = onDelete,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 16.dp)
                )
                OfficialTypePill(
                    post.type,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 38.dp, end = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun OfficialPostPortraitContent(
    post: OfficialPostItem,
    onOpenAuthor: () -> Unit,
    onReadMore: () -> Unit,
    onOpenMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OfficialAuthorHeader(
            post = post,
            onOpenAuthor = onOpenAuthor
        )
        Spacer(Modifier.height(16.dp))
        if (post.mediaUrl.isNullOrBlank()) {
            OfficialPostTextOnlyBlock(
                post = post,
                onReadMore = onReadMore,
                modifier = Modifier.weight(1f)
            )
        } else {
            OfficialPostMedia(
                post = post,
                onOpenMedia = onOpenMedia,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            )
            Spacer(Modifier.height(12.dp))
            OfficialPostTextBlock(post, titleSize = 17, compact = true)
            OfficialReadMoreLink(post = post, onReadMore = onReadMore)
        }
    }
}

@Composable
private fun OfficialPostLandscapeContent(
    post: OfficialPostItem,
    onOpenAuthor: () -> Unit,
    onReadMore: () -> Unit,
    onOpenMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (post.mediaUrl.isNullOrBlank()) {
        Column(modifier = modifier) {
            OfficialAuthorHeader(
                post = post,
                onOpenAuthor = onOpenAuthor,
                modifier = Modifier.padding(end = 96.dp)
            )
            Spacer(Modifier.height(16.dp))
            OfficialPostTextOnlyBlock(
                post = post,
                onReadMore = onReadMore,
                alignReadMoreEnd = true,
                modifier = Modifier.weight(1f)
            )
        }
        return
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OfficialPostMedia(
            post = post,
            onOpenMedia = onOpenMedia,
            modifier = Modifier
                .weight(1.08f)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(0.92f)
                .fillMaxHeight()
        ) {
            OfficialAuthorHeader(
                post = post,
                onOpenAuthor = onOpenAuthor,
                modifier = Modifier.padding(end = 96.dp)
            )
            Spacer(Modifier.height(18.dp))
            OfficialPostTextBlock(post, titleSize = 22, compact = false, modifier = Modifier.weight(1f, fill = false))
            OfficialReadMoreLink(post = post, onReadMore = onReadMore)
        }
    }
}

@Composable
private fun OfficialPostTextOnlyBlock(
    post: OfficialPostItem,
    onReadMore: () -> Unit,
    alignReadMoreEnd: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                OfficialPostTextBlock(
                    post = post,
                    titleSize = 26,
                    compact = false
                )
            }
        }
        OfficialReadMoreLink(
            post = post,
            onReadMore = onReadMore,
            alignEnd = alignReadMoreEnd
        )
    }
}

@Composable
private fun OfficialAuthorHeader(
    post: OfficialPostItem,
    onOpenAuthor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AvatarImage(
            name = post.author.displayName,
            avatarUrl = post.author.avatarUrl,
            isOfficial = true,
            modifier = Modifier
                .size(58.dp)
                .clickable(onClick = onOpenAuthor)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    post.author.displayName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                CompactIcon(
                    Icons.Filled.Verified,
                    contentDescription = null,
                    tint = Color(0xFF2F80ED),
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                post.author.neighborhood.ifBlank { stringResource(R.string.official_account_fallback) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OfficialTypePill(
    type: OfficialPostType,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = Color(0xFF2BA84A).copy(alpha = if (template.resolvedTheme == QuataResolvedTheme.Dark) 0.18f else 0.10f),
        contentColor = Color(0xFF2BA84A),
        shape = RoundedCornerShape(100.dp),
        modifier = modifier
    ) {
        Text(
            text = type.label(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun OfficialPostTextBlock(
    post: OfficialPostItem,
    titleSize: Int,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            post.title,
            fontWeight = FontWeight.Black,
            fontSize = titleSize.sp,
            lineHeight = (titleSize + 5).sp,
            maxLines = if (compact) 2 else 4,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(54.dp)
                .background(Color(0xFF2BA84A), RoundedCornerShape(20.dp))
        )
        Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        Text(
            post.summary.ifBlank { post.contentPlain },
            fontSize = if (compact) 14.sp else 16.sp,
            lineHeight = if (compact) 20.sp else 23.sp,
            maxLines = if (compact) 3 else 7,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OfficialReadMoreLink(
    post: OfficialPostItem,
    onReadMore: () -> Unit,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    val hasReadableBody = post.contentPlain.isNotBlank() && post.contentPlain.trim() != post.summary.trim()
    if (!hasReadableBody && post.linkUrl.isNullOrBlank()) return
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        Text(
            localizedOfficialReadMoreLabel(post.readMoreLabel),
            color = if (template.resolvedTheme == QuataResolvedTheme.Dark) Color(0xFF2EA7FF) else Color(0xFF17954B),
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onReadMore)
        )
    }
}

@Composable
private fun OfficialPostMedia(
    post: OfficialPostItem,
    onOpenMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediaUrl = post.mediaUrl?.takeIf { it.isNotBlank() } ?: return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpenMedia),
        contentAlignment = Alignment.Center
    ) {
        if (post.mediaType == OfficialMediaType.Image) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = post.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            VideoAttachmentThumbnail(
                uri = mediaUrl,
                name = post.title,
                showPlayButton = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun OfficialPostActionRail(
    post: OfficialPostItem,
    rank: Int,
    isLandscape: Boolean,
    canPublish: Boolean,
    canModerate: Boolean,
    onCreate: () -> Unit,
    onOpenLive: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    QuataFeedActionRail(
        likes = post.likesCount,
        isLiked = post.isLikedByCurrentUser,
        comments = post.commentsCount,
        postRank = rank,
        isLandscape = isLandscape,
        likeLabel = stringResource(R.string.feed_like),
        commentsLabel = stringResource(R.string.feed_comments),
        shareLabel = stringResource(R.string.feed_share),
        rankLabel = stringResource(R.string.feed_rank),
        liveLabel = stringResource(R.string.common_live),
        publishLabel = stringResource(R.string.official_create),
        deleteLabel = stringResource(R.string.feed_delete_post),
        showReport = false,
        showDelete = canModerate,
        showPublish = canPublish,
        onLike = onLike,
        onOpenComments = onComment,
        onShare = onShare,
        onOpenLive = onOpenLive,
        onDelete = onDelete,
        onPublish = onCreate,
        modifier = modifier
    )
}

@Composable
private fun OfficialPostSkeleton(modifier: Modifier = Modifier) {
    val template = quataTheme()
    Card(
        colors = CardDefaults.cardColors(containerColor = template.colors.surfaceAlt),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {}
}

@Composable
private fun OfficialEmptyState(modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = quataTheme().colors.surfaceAlt),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = QuataOrange, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.official_empty), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun OfficialPostDetailPanel(
    post: OfficialPostItem,
    onDismiss: () -> Unit
) {
    val template = quataTheme()
    QuataStandardFloatingPanel(onDismiss = onDismiss, template = template) { panelModifier, isLandscape ->
        Column(
            modifier = panelModifier.padding(
                start = 18.dp,
                top = if (isLandscape) 18.dp else 10.dp,
                end = 18.dp,
                bottom = if (isLandscape) 18.dp else 48.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    localizedOfficialReadMoreLabel(post.readMoreLabel),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_close))
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    QuataRichTextRenderer(
                        html = post.contentHtml,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = post.contentPlain
                    )
                }
                post.linkUrl?.takeIf { it.isNotBlank() }?.let { link ->
                    item {
                        Text(
                            link,
                            color = if (template.resolvedTheme == QuataResolvedTheme.Dark) Color(0xFF2EA7FF) else Color(0xFF17954B),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficialMediaViewerDialog(
    post: OfficialPostItem,
    onDismiss: () -> Unit
) {
    val url = post.mediaUrl?.takeIf { it.isNotBlank() } ?: return
    val mime = when (post.mediaType) {
        OfficialMediaType.Image -> "image/*"
        OfficialMediaType.Video -> "video/*"
        null -> "*/*"
    }
    AttachmentViewerDialog(
        attachment = AttachmentPreview(
            name = post.title.ifBlank { stringResource(R.string.official_post_default_title) },
            uri = url,
            mimeType = mime
        ),
        onDismiss = onDismiss
    )
}

private data class OfficialPostRankingInfo(
    val position: Int,
    val likes: Int
)

private const val OfficialOlderPostsPrefetchDistance = 8

private fun calculateOfficialPostRankingMap(posts: List<OfficialPostItem>): Map<String, OfficialPostRankingInfo> =
    posts
        .sortedWith(compareByDescending<OfficialPostItem> { it.likesCount }.thenByDescending { it.createdAt })
        .mapIndexed { index, post ->
            post.id to OfficialPostRankingInfo(position = index + 1, likes = post.likesCount)
        }
        .toMap()

@Composable
private fun OfficialPostType.label(): String = when (this) {
    OfficialPostType.Announcement -> stringResource(R.string.official_type_announcement)
    OfficialPostType.News -> stringResource(R.string.official_type_news)
    OfficialPostType.Event -> stringResource(R.string.official_type_event)
    OfficialPostType.Urgent -> stringResource(R.string.official_type_urgent)
}

private fun shareOfficialPost(context: Context, post: OfficialPostItem) {
    val body = buildString {
        append(post.title)
        append("\n\n")
        append(post.summary.ifBlank { post.contentPlain })
        append("\n\n")
        append(quataOfficialPostUrl(post.id))
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(intent, post.title))
}
