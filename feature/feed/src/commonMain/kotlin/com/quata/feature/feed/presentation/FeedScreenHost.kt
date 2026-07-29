package com.quata.feature.feed.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.text.extractPostMeta
import com.quata.core.text.parsePostShortcodeContent
import com.quata.core.ui.components.QuataCommentInputContent
import com.quata.core.ui.components.QuataCommentInputStrings
import com.quata.core.ui.components.QuataCommentRowContent
import com.quata.core.ui.components.QuataCommentRowStrings
import com.quata.core.ui.components.QuataCommentsPanelHeaderContent
import com.quata.core.ui.components.QuataCommentsPanelPortraitContent
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataFeedPullRefreshIndicator
import com.quata.core.ui.components.rememberQuataFeedPullRefreshState
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.feature.feed.domain.FeedRepository
import kotlinx.coroutines.launch

/** Text and native boundaries required by the common Feed product surface. */
data class FeedScreenStrings(
    val loadingError: String = "No se pudo cargar el feed.",
    val empty: String = "Aún no hay publicaciones disponibles.",
    val retry: String = "Reintentar",
    val like: String = "Me gusta",
    val comments: String = "Comentarios",
    val share: String = "Compartir",
    val rank: String = "Ranking",
    val live: String = "LIVE",
    val publish: String = "Publicar",
    val report: String = "Reportar",
    val delete: String = "Eliminar",
    val deleteTitle: String = "Eliminar publicación",
    val deleteMessage: String = "Esta acción no se puede deshacer.",
    val reportSuccess: String = "Publicación reportada",
    val deleteSuccess: String = "Publicación eliminada",
    val cancel: String = "Cancelar",
    val close: String = "Cerrar",
    val commentPlaceholder: String = "Escribe un comentario",
    val send: String = "Enviar",
    val reply: String = "Responder",
    val replyTo: (String) -> String = { "En respuesta a $it" },
    val locationLabel: @Composable (String) -> String = { it },
)

/** Platform-only rendering and service hooks. The Feed state machine stays in commonMain. */
data class FeedScreenPlatformSlots(
    val media: @Composable BoxScope.(Post, Boolean, Long, (Long) -> Unit) -> Unit,
    val avatar: @Composable (Post) -> Unit = {},
    val rankingAvatar: @Composable (String, String?) -> Unit = { _, _ -> },
    val share: suspend (Post) -> Unit = {},
    val message: (String) -> Unit = {},
)

/**
 * The sole Compose Feed root used by Android, Wasm and iOS.
 *
 * It owns the common ViewModel lifetime, paging, focus/reset restoration and every Feed
 * mutation. Hosts only provide native media/avatar/share/message adapters and navigation.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreenHost(
    padding: PaddingValues,
    repository: FeedRepository,
    stateHolder: FeedStateHolder? = null,
    slots: FeedScreenPlatformSlots,
    currentUserId: String? = null,
    focusedPostId: String? = null,
    feedResetToken: Int = 0,
    networkReconnectToken: Long = 0L,
    isLandscape: Boolean = false,
    strings: FeedScreenStrings = FeedScreenStrings(),
    onFocusedPostHandled: () -> Unit = {},
    onAuthRequired: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    onCreatePost: () -> Unit = {},
    onReportComment: (String) -> Unit = {},
    onCommentsVisibilityChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ownedViewModel = remember(repository) { FeedViewModel(repository) }
    val viewModel = stateHolder ?: ownedViewModel
    DisposableEffect(ownedViewModel, stateHolder) { onDispose { if (stateHolder == null) ownedViewModel.close() } }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var commentsPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletionPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeletedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var liveOpen by rememberSaveable { mutableStateOf(false) }
    var handledFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var retainedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAppliedRetainedPost by remember { mutableStateOf(retainedPostId == null) }
    var handledReset by rememberSaveable { mutableStateOf(feedResetToken) }
    val videoPositions = remember { mutableMapOf<String, Long>() }
    val pagerState = rememberPagerState(pageCount = { state.posts.size })
    val effectiveCurrentUserId = currentUserId ?: state.currentUser?.id
    val canParticipate = effectiveCurrentUserId != null
    val ranks = remember(state.posts) { state.posts.sortedWith(compareByDescending<Post> { it.likesCount }.thenByDescending { it.createdAt }).mapIndexed { index, post -> post.id to index + 1 }.toMap() }
    val canPullRefresh = pagerState.currentPage == 0 && !state.isRefreshing && commentsPostId == null && !liveOpen
    val pullRefreshState = rememberQuataFeedPullRefreshState(
        enabled = canPullRefresh,
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) },
    )

    LaunchedEffect(commentsPostId, isLandscape) { onCommentsVisibilityChanged(commentsPostId != null && isLandscape) }
    DisposableEffect(Unit) { onDispose { onCommentsVisibilityChanged(false) } }

    LaunchedEffect(focusedPostId) {
        if (focusedPostId != null && focusedPostId != handledFocus) viewModel.onEvent(FeedUiEvent.Refresh)
    }
    LaunchedEffect(networkReconnectToken) {
        if (networkReconnectToken != 0L) viewModel.onEvent(FeedUiEvent.Refresh)
    }
    LaunchedEffect(state.posts, pendingDeletedPostId) {
        val deletedId = pendingDeletedPostId ?: return@LaunchedEffect
        if (state.posts.none { it.id == deletedId }) {
            slots.message(strings.deleteSuccess)
            pendingDeletedPostId = null
        }
    }
    LaunchedEffect(focusedPostId, state.posts) {
        val index = state.posts.indexOfFirst { it.id == focusedPostId }
        if (focusedPostId != null && focusedPostId != handledFocus && index >= 0) {
            pagerState.scrollToPage(index)
            retainedPostId = focusedPostId
            hasAppliedRetainedPost = true
            handledFocus = focusedPostId
            onFocusedPostHandled()
        }
    }
    LaunchedEffect(feedResetToken, state.posts.size) {
        if (feedResetToken != handledReset && focusedPostId == null && state.posts.isNotEmpty()) {
            pagerState.scrollToPage(0)
            retainedPostId = state.posts.first().id
            hasAppliedRetainedPost = true
            handledReset = feedResetToken
        }
    }
    LaunchedEffect(retainedPostId, state.posts, focusedPostId) {
        val target = retainedPostId
        if (!hasAppliedRetainedPost && focusedPostId == null && target != null && state.posts.isNotEmpty()) {
            state.posts.indexOfFirst { it.id == target }.takeIf { it >= 0 && it != pagerState.currentPage }
                ?.let { pagerState.scrollToPage(it) }
            hasAppliedRetainedPost = true
        }
    }

    when {
        state.error != null && state.posts.isEmpty() -> FeedStatusContent(state.error ?: strings.loadingError, strings.retry, { viewModel.onEvent(FeedUiEvent.Refresh) }, modifier.padding(padding))
        state.posts.isEmpty() && !state.isLoading -> FeedStatusContent(strings.empty, strings.retry, { viewModel.onEvent(FeedUiEvent.Refresh) }, modifier.padding(padding))
        else -> FeedPagerViewportContent(padding, modifier.nestedScroll(pullRefreshState.nestedScrollConnection)) {
            FeedReelPagerContent(
                pagerState = pagerState,
                posts = state.posts,
                hasMoreOlderPosts = state.hasMoreOlderPosts,
                isLoadingOlder = state.isLoadingOlder,
                onPostDisplayed = { visible, next ->
                    if (hasAppliedRetainedPost) retainedPostId = visible.id
                    viewModel.onEvent(FeedUiEvent.PostDisplayed(visible.id, next?.id))
                },
                onLoadOlder = { viewModel.onEvent(FeedUiEvent.LoadOlderPage) },
            ) { _, post, isCurrent ->
                val canDelete = post.author.id == effectiveCurrentUserId || state.currentUser?.isAdmin == true
                FeedReelPostContent(
                    post = post,
                    postRank = ranks[post.id] ?: 1,
                    isLandscape = isLandscape,
                    canDelete = canDelete,
                    strings = FeedReelStrings(strings.like, strings.comments, strings.share, strings.rank, strings.live, strings.publish, strings.report, strings.delete, strings.locationLabel),
                    media = {
                        ReelMediaVariantContent(
                            hasVideo = post.videoUrl != null,
                            hasImage = post.imageUrl != null,
                            hasText = post.text.parsePostShortcodeContent().cleanText.isNotBlank(),
                            video = { slots.media(this, post, isCurrent, post.videoUrl?.let { videoPositions[it] } ?: 0L) { position -> post.videoUrl?.let { videoPositions[it] = position } } },
                            image = { slots.media(this, post, isCurrent, 0L) {} },
                            text = {
                                val shortcode = post.text.parsePostShortcodeContent()
                                val meta = post.text.extractPostMeta()
                                TextOnlyReelContent(
                                    stableId = post.id,
                                    displayText = shortcode.cleanText,
                                    seedText = post.text.cleanTextCanvasSeedBody(),
                                    patternId = meta.textPattern,
                                    readMoreText = "Leer más",
                                    readerDismissButton = { readerModifier, dismiss -> CompactIconButton(onClick = dismiss, modifier = readerModifier) { CompactIcon(Icons.Filled.Close, strings.close) } },
                                )
                            },
                        )
                    },
                    avatar = { slots.avatar(post) },
                    onOpenComments = { commentsPostId = post.id },
                    onOpenLive = { liveOpen = true },
                    onLike = { if (canParticipate) viewModel.onEvent(FeedUiEvent.ToggleLike(post.id)) else onAuthRequired() },
                    onDelete = { deletionPostId = post.id },
                    onShare = { scope.launch { slots.share(post) } },
                    onReport = {
                        if (post.isReportedByCurrentUser) Unit
                        else if (canParticipate) { viewModel.onEvent(FeedUiEvent.ReportPost(post.id)); slots.message(strings.reportSuccess) }
                        else onAuthRequired()
                    },
                    onCreatePost = { if (canParticipate) onCreatePost() else onAuthRequired() },
                )
            }
            QuataFeedPullRefreshIndicator(
                state = pullRefreshState,
                isRefreshing = state.isRefreshing && pagerState.currentPage == 0,
                refreshContentDescription = "Actualizar",
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }

    state.posts.firstOrNull { it.id == commentsPostId }?.let { post ->
        FeedCommentsDialog(
            post = post,
            canParticipate = canParticipate,
            strings = strings,
            onAuthRequired = onAuthRequired,
            onReportComment = onReportComment,
            onAddComment = { viewModel.onEvent(FeedUiEvent.AddComment(post.id, it)) },
            onDismiss = { commentsPostId = null },
        )
    }
    deletionPostId?.let { id ->
        FeedDeletePostConfirmationContent(strings.deleteTitle, strings.deleteMessage, strings.delete, strings.cancel, {
            viewModel.onEvent(FeedUiEvent.DeletePost(id))
            pendingDeletedPostId = id
            deletionPostId = null
        }) { deletionPostId = null }
    }
    if (liveOpen) Dialog(onDismissRequest = { liveOpen = false }) {
        Surface(Modifier.fillMaxSize().padding(20.dp)) {
            FeedLiveRankingDialogContent(
                posts = state.posts,
                rankForPost = { ranks[it.id] ?: 1 },
                postTypeLabel = { if (it.videoUrl != null) "Vídeo" else "Publicación" },
                panel = { items, dismiss, open -> QuataLiveRankingPanelContent(items, isLandscape, QuataLiveRankingStrings("En directo", "Publicaciones destacadas", "Publicaciones monitorizadas", "Actualizado", strings.live, strings.close, "Abrir"), { item -> slots.rankingAvatar(item.avatarName, item.avatarUrl) }, dismiss, open) },
                onDismiss = { liveOpen = false },
                onOpenPost = { post ->
                    scope.launch { pagerState.scrollToPage(state.posts.indexOf(post).coerceAtLeast(0)) }
                    liveOpen = false
                },
            )
        }
    }
}

@Composable
private fun FeedCommentsDialog(
    post: Post,
    canParticipate: Boolean,
    strings: FeedScreenStrings,
    onAuthRequired: () -> Unit,
    onReportComment: (String) -> Unit,
    onAddComment: (PostComment) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(post.id) { mutableStateOf(TextFieldValue()) }
    var replyTo by remember(post.id) { mutableStateOf<PostComment?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxSize().padding(16.dp)) {
            QuataCommentsPanelPortraitContent(
                header = { QuataCommentsPanelHeaderContent(strings.comments, post.comments.size, { modifier -> TextButton(onClick = onDismiss, modifier = modifier) { Text(strings.close) } }) },
                comments = { modifier -> LazyColumn(modifier.heightIn(min = 180.dp)) { items(post.comments, key = { it.id }) { comment -> QuataCommentRowContent(comment, comment.timestamp, QuataCommentRowStrings(strings.replyTo, strings.report, strings.reply), onReply = { replyTo = comment }, onReport = { if (canParticipate) onReportComment(comment.id) else onAuthRequired() }) } } },
                input = { modifier -> QuataCommentInputContent(post.id, draft, replyTo, canParticipate, "Tú", QuataCommentInputStrings(strings.commentPlaceholder, strings.send), { "ahora" }, {}, { draft = it }, onAuthRequired, onAddComment, { draft = TextFieldValue(); replyTo = null }, {}, modifier.fillMaxWidth()) },
            )
        }
    }
}
