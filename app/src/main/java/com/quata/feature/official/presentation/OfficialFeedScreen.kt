package com.quata.feature.official.presentation

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
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
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
import com.quata.feature.official.domain.calculateOfficialPostRanking
import kotlinx.coroutines.launch

@Composable
fun OfficialFeedScreen(
    padding: PaddingValues,
    repository: OfficialRepository,
    shareService: ShareService,
    currentUserId: String?,
    focusedPostId: String? = null,
    onFocusedPostHandled: () -> Unit = {},
    onAuthRequired: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: (() -> Unit)? = null,
    onReportComment: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OfficialFeedAndroidViewModel = viewModel(factory = OfficialFeedAndroidViewModel.factory(repository))
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
    val postRanks = remember(state.posts) { calculateOfficialPostRanking(state.posts) }
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

    OfficialFeedViewportContent(
        padding = padding,
        modifier = modifier.nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
            when {
                state.isLoading && state.posts.isEmpty() -> {
                    OfficialLoadingContent(
                        canPublish = canPublish,
                        strings = OfficialStatusStrings(stringResource(R.string.official_empty), stringResource(R.string.official_create)),
                        onCreate = { requestCreateOfficialPost() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.posts.isEmpty() -> {
                    OfficialEmptyContent(
                        canPublish = canPublish,
                        strings = OfficialStatusStrings(stringResource(R.string.official_empty), stringResource(R.string.official_create)),
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
                            onShare = {
                                scope.launch {
                                    shareService.share(
                                        SharePayload(
                                            text = officialPostShareText(post),
                                            title = post.title
                                        )
                                    )
                                }
                            },
                            onDelete = { postPendingDelete = post },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
                QuataFeedPullRefreshIndicator(
                    state = pullRefreshState,
                    isRefreshing = state.isRefreshing && pagerState.currentPage == 0,
                    refreshContentDescription = stringResource(R.string.common_refresh),
                    modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(4f)
            )
            if (state.isLoadingOlder) {
                OfficialOlderPostsLoadingContent(
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
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
                    profileId = post.author.id,
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
        OfficialDeleteConfirmationDialogContent(
            title = stringResource(R.string.official_delete_confirm_title),
            message = stringResource(R.string.official_delete_confirm_message),
            confirmLabel = stringResource(R.string.common_confirm),
            cancelLabel = stringResource(R.string.common_cancel),
            onDismiss = { postPendingDelete = null },
            onConfirm = {
                viewModel.onEvent(OfficialFeedUiEvent.DeletePost(post.id))
                postPendingDelete = null
            },
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
    OfficialPagerPostPageContent(
        modifier = modifier,
        card = { cardModifier ->
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
            modifier = cardModifier,
        )
        },
    )
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
    val windowInfo = rememberQuataWindowLayoutInfo()
    val isLandscape = !forcePortraitLayout && windowInfo.isLandscape
    val mediaSlot: (@Composable (Modifier) -> Unit)? =
        if (post.mediaUrl.isNullOrBlank()) null else { slotModifier ->
            OfficialPostMedia(post, onOpenMedia, slotModifier)
        }
    OfficialPostCardContent(
        post = post,
        typeLabel = post.type.label(),
        readMoreLabel = localizedOfficialReadMoreLabel(post.readMoreLabel),
        isLandscape = isLandscape,
        author = { slotModifier -> OfficialAuthorHeader(post, onOpenAuthor, slotModifier) },
        media = mediaSlot,
        actionRail = { landscape, slotModifier ->
            OfficialPostActionRail(
                post, rank, landscape, canPublish, canModerate, onCreate, onOpenLive,
                onLike, onComment, onShare, onDelete, slotModifier,
            )
        },
        overflowAction = { slotModifier ->
            QuataFeedOverflowActionButton(
                postRank = rank,
                rankLabel = stringResource(R.string.feed_rank),
                liveLabel = stringResource(R.string.common_live),
                reportLabel = null,
                showReport = false,
                onOpenLive = onOpenLive,
                onReport = {},
                modifier = slotModifier,
            )
        },
        onReadMore = onReadMore,
        modifier = modifier,
    )
}

@Composable
private fun OfficialAuthorHeader(
    post: OfficialPostItem,
    onOpenAuthor: () -> Unit,
    modifier: Modifier = Modifier
) {
    OfficialAuthorHeaderContent(
        displayName = post.author.displayName,
        neighborhood = post.author.neighborhood,
        fallbackNeighborhood = stringResource(R.string.official_account_fallback),
        avatar = {
            AvatarImage(
            name = post.author.displayName,
            avatarUrl = post.author.avatarUrl,
            isOfficial = true,
            profileId = post.author.id,
            modifier = Modifier
                .size(58.dp)
                .clickable(onClick = onOpenAuthor)
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun OfficialPostMedia(
    post: OfficialPostItem,
    onOpenMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mediaUrl = post.mediaUrl?.takeIf { it.isNotBlank() } ?: return
    OfficialPostMediaFrameContent(
        onOpenMedia = onOpenMedia,
        media = { mediaModifier ->
            if (post.mediaType == OfficialMediaType.Image) {
                AsyncImage(
                    model = mediaUrl,
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = mediaModifier,
                )
            } else {
                VideoAttachmentThumbnail(
                    uri = mediaUrl,
                    name = post.title,
                    showPlayButton = true,
                    modifier = mediaModifier,
                )
            }
        },
        modifier = modifier,
    )
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
internal fun OfficialPostDetailPanel(
    post: OfficialPostItem,
    onDismiss: () -> Unit
) {
    OfficialPostDetailPanelContent(
        title = localizedOfficialReadMoreLabel(post.readMoreLabel),
        closeLabel = stringResource(R.string.common_close),
        link = post.linkUrl,
        onDismiss = onDismiss,
        articleContent = { slotModifier ->
            QuataRichTextRenderer(
                html = post.contentHtml,
                modifier = slotModifier,
                placeholder = post.contentPlain,
            )
        },
    )
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

private const val OfficialOlderPostsPrefetchDistance = 8

@Composable
private fun OfficialPostType.label(): String = when (this) {
    OfficialPostType.Announcement -> stringResource(R.string.official_type_announcement)
    OfficialPostType.News -> stringResource(R.string.official_type_news)
    OfficialPostType.Event -> stringResource(R.string.official_type_event)
    OfficialPostType.Urgent -> stringResource(R.string.official_type_urgent)
}

private fun officialPostShareText(post: OfficialPostItem): String = buildString {
        append(post.title)
        append("\n\n")
        append(post.summary.ifBlank { post.contentPlain })
        append("\n\n")
        append(quataOfficialPostUrl(post.id))
    }
