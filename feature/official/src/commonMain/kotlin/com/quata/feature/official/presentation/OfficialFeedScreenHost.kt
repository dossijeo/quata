package com.quata.feature.official.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.quata.core.model.PostComment
import com.quata.core.navigation.quataOfficialPostUrl
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.SharePayload
import com.quata.core.ui.components.QuataFeedPullRefreshIndicator
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
import com.quata.core.ui.components.rememberQuataFeedPullRefreshState
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.official.domain.calculateOfficialPostRanking
import kotlinx.coroutines.launch

/** Localized copy and platform-visible feedback for the shared Official product surface. */
data class OfficialFeedScreenStrings(
    val empty: String = "No hay comunicados oficiales disponibles.",
    val create: String = "Crear comunicado",
    val retry: String = "Reintentar",
    val loadingError: String = "No se pudieron cargar los comunicados oficiales.",
    val like: String = "Me gusta",
    val comments: String = "Comentarios",
    val share: String = "Compartir",
    val rank: String = "Ranking",
    val live: String = "LIVE",
    val delete: String = "Eliminar",
    val close: String = "Cerrar",
    val profile: String = "Perfil",
    val readMore: String = "Leer más",
    val refresh: String = "Actualizar",
    val officialAccountFallback: String = "Cuenta oficial",
    val deleteTitle: String = "Eliminar comunicado",
    val deleteMessage: String = "Esta acción no se puede deshacer.",
    val confirm: String = "Confirmar",
    val cancel: String = "Cancelar",
    val deleted: String = "Comunicado eliminado",
    val shareUnavailable: String = "No se puede compartir este comunicado en este dispositivo.",
    val shareFailed: String = "No se pudo compartir el comunicado.",
)

/** The only target-specific seams: rendering and externally-owned services/navigation. */
data class OfficialFeedScreenPlatformSlots(
    val avatar: @Composable (OfficialPostItem, Modifier) -> Unit,
    val media: @Composable (OfficialPostItem, Modifier, () -> Unit) -> Unit,
    val article: @Composable (OfficialPostItem, Modifier) -> Unit = { post, modifier -> Text(post.contentPlain.ifBlank { post.summary }, modifier) },
    val mediaViewer: @Composable (OfficialPostItem, () -> Unit) -> Unit = { _, _ -> },
    val openUrl: (String) -> Unit = {},
    val share: suspend (SharePayload) -> PlatformResult<Unit> = { PlatformResult.Unsupported },
    val message: (String) -> Unit = {},
    val showComposeMessage: Boolean = false,
    val canCreateOfficialPost: Boolean = false,
    val rankingAvatar: @Composable (QuataLiveRankingItem) -> Unit = {},
    val floatingPanel: @Composable (onDismiss: () -> Unit, content: @Composable (Modifier, Boolean) -> Unit) -> Unit =
        { dismiss, content -> QuataStandardFloatingPanelContent(onDismiss = dismiss, content = content) },
)

/**
 * Sole Official screen root shared by Android, Wasm and iOS.
 *
 * It deliberately owns pager restoration, deep-link focus, mutations and overlays. Platform
 * launchers may only supply media/rich text/avatar adapters and leave-screen actions.
 */
@Composable
fun OfficialFeedScreenHost(
    padding: PaddingValues,
    repository: OfficialRepository,
    slots: OfficialFeedScreenPlatformSlots,
    currentUserId: String? = null,
    focusedPostId: String? = null,
    strings: OfficialFeedScreenStrings = OfficialFeedScreenStrings(),
    onFocusedPostHandled: () -> Unit = {},
    onAuthRequired: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    onCreateOfficialPost: () -> Unit = {},
    onReportComment: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(repository) { OfficialFeedViewModel(repository) }
    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val windowInfo = rememberQuataWindowLayoutInfo()
    var readMorePost by rememberSaveable { mutableStateOf<String?>(null) }
    var commentsPost by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaPost by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePost by rememberSaveable { mutableStateOf<String?>(null) }
    var liveOpen by rememberSaveable { mutableStateOf(false) }
    var handledFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var retainedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(retainedPostId == null) }
    val pagerState = rememberPagerState(pageCount = { state.posts.size.coerceAtLeast(1) })
    val effectiveUserId = currentUserId ?: state.currentUser?.id
    val canPublish = state.currentUser?.isOfficial == true && slots.canCreateOfficialPost
    val ranks = remember(state.posts) { calculateOfficialPostRanking(state.posts) }
    val canPullRefresh = pagerState.currentPage == 0 && !state.isRefreshing && commentsPost == null && readMorePost == null && !liveOpen
    val pullRefresh = rememberQuataFeedPullRefreshState(canPullRefresh, state.isRefreshing) { viewModel.onEvent(OfficialFeedUiEvent.Refresh) }
    fun message(value: String) {
        slots.message(value)
        if (slots.showComposeMessage) scope.launch { snackbar.showSnackbar(value) }
    }
    fun create() { if (effectiveUserId == null) onAuthRequired() else onCreateOfficialPost() }

    LaunchedEffect(state.message) {
        if (state.message == OfficialFeedMessages.PostDeleted) { message(strings.deleted); viewModel.onEvent(OfficialFeedUiEvent.ClearMessage) }
    }
    LaunchedEffect(focusedPostId) {
        focusedPostId?.takeIf { it != handledFocus && state.posts.none { post -> post.id == it } }?.let { viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(it)) }
    }
    LaunchedEffect(focusedPostId, state.posts) {
        val target = focusedPostId ?: return@LaunchedEffect
        val index = state.posts.indexOfFirst { it.id == target }
        if (target != handledFocus && index >= 0) { pagerState.scrollToPage(index); retainedPostId = target; restored = true; handledFocus = target; onFocusedPostHandled() }
    }
    LaunchedEffect(retainedPostId, state.posts, focusedPostId) {
        val index = state.posts.indexOfFirst { it.id == retainedPostId }
        if (!restored && focusedPostId == null && index >= 0) { pagerState.scrollToPage(index); restored = true }
    }
    LaunchedEffect(pagerState.currentPage, state.posts) { if (restored) state.posts.getOrNull(pagerState.currentPage)?.let { retainedPostId = it.id } }

    Box(modifier.fillMaxSize()) {
        when {
            state.error != null && state.posts.isEmpty() -> OfficialHostFailure(state.error ?: strings.loadingError, strings.retry, { viewModel.onEvent(OfficialFeedUiEvent.Refresh) }, Modifier.fillMaxSize())
            else -> OfficialFeedPagerContent(
                padding = padding,
                pagerState = pagerState,
                posts = state.posts,
                hasMoreOlderPosts = state.hasMoreOlderPosts,
                isLoadingOlder = state.isLoadingOlder,
                isInitialLoading = state.isLoading,
                onLoadOlder = { viewModel.onEvent(OfficialFeedUiEvent.LoadOlderPage) },
                emptyContent = { loading -> if (loading) OfficialLoadingContent(canPublish, OfficialStatusStrings(strings.empty, strings.create), ::create, Modifier.fillMaxSize()) else OfficialEmptyContent(canPublish, OfficialStatusStrings(strings.empty, strings.create), ::create, Modifier.fillMaxSize()) },
                pageContent = { index, post, _ ->
                    OfficialPagerPostPageContent(card = { cardModifier ->
                        OfficialPostCardContent(
                            post = post, typeLabel = post.type.remoteValue.uppercase(), readMoreLabel = post.readMoreLabel.ifBlank { strings.readMore }, isLandscape = windowInfo.isLandscape,
                            author = { authorModifier -> OfficialAuthorHeaderContent(post.author.displayName, post.author.neighborhood, strings.officialAccountFallback, { slots.avatar(post, Modifier.size(58.dp)) }, authorModifier.clickable { onOpenUserProfile(post.author.id) }) },
                            media = post.mediaUrl?.takeIf(String::isNotBlank)?.let { { mediaModifier -> slots.media(post, mediaModifier) { mediaPost = post.id } } },
                            actionRail = { landscape, railModifier ->
                                OfficialPostActionRailContent(
                                    post = post,
                                    rank = ranks[post.id]?.position ?: index + 1,
                                    isLandscape = landscape,
                                    canPublish = canPublish,
                                    canModerate = state.currentUser?.isAdmin == true || post.author.id == effectiveUserId,
                                    strings = OfficialPostActionRailStrings(strings.like, strings.comments, strings.share, strings.rank, strings.live, strings.create, strings.delete),
                                    onCreate = ::create,
                                    onOpenLive = { liveOpen = true },
                                    onLike = { if (effectiveUserId == null) onAuthRequired() else viewModel.onEvent(OfficialFeedUiEvent.ToggleLike(post.id)) },
                                    onComment = { commentsPost = post.id },
                                    onShare = {
                                        scope.launch {
                                            when (slots.share(officialSharePayload(post))) {
                                                is PlatformResult.Success<*> -> Unit
                                                PlatformResult.Unsupported -> message(strings.shareUnavailable)
                                                else -> message(strings.shareFailed)
                                            }
                                        }
                                    },
                                    onDelete = { deletePost = post.id },
                                    modifier = railModifier,
                                )
                            },
                            onReadMore = { readMorePost = post.id }, modifier = cardModifier,
                        )
                    }, modifier = Modifier.fillMaxSize())
                },
                modifier = Modifier.fillMaxSize().nestedScroll(pullRefresh.nestedScrollConnection),
            ) {
                QuataFeedPullRefreshIndicator(pullRefresh, state.isRefreshing && pagerState.currentPage == 0, strings.refresh, Modifier.align(Alignment.TopCenter))
                if (state.isLoadingOlder) OfficialOlderPostsLoadingContent(Modifier.align(Alignment.BottomCenter))
            }
        }
        if (slots.showComposeMessage) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    state.posts.firstOrNull { it.id == readMorePost }?.let { post ->
        OfficialPostDetailPanelContent(post.title, strings.close, post.linkUrl, { readMorePost = null }, { slots.article(post, it) }, { OfficialAuthorHeaderContent(post.author.displayName, post.author.neighborhood, strings.officialAccountFallback, { slots.avatar(post, Modifier.size(58.dp)) }, it.clickable { onOpenUserProfile(post.author.id) }) }, post.mediaUrl?.takeIf(String::isNotBlank)?.let { { modifier -> slots.media(post, modifier) { mediaPost = post.id } } }, post.linkUrl?.let { link -> { modifier -> TextButton({ slots.openUrl(link) }, modifier) { Text(link) } } }, { modifier -> TextButton({ onOpenUserProfile(post.author.id) }, modifier) { Text(strings.profile) } })
    }
    OfficialCommentsPanelEntryContent(state.posts.firstOrNull { it.id == commentsPost }, state.posts, effectiveUserId, onAuthRequired, { postId, comment -> viewModel.onEvent(OfficialFeedUiEvent.AddComment(postId, comment)) }, onReportComment, { commentsPost = null }) { post, canParticipate, add, report, dismiss ->
        OfficialCommentsPanelContent(post, canParticipate, OfficialCommentsStrings(close = strings.close, title = strings.comments), onAuthRequired, add, report, dismiss)
    }
    state.posts.firstOrNull { it.id == deletePost }?.let { post -> OfficialDeleteConfirmationDialogContent(strings.deleteTitle, strings.deleteMessage, strings.confirm, strings.cancel, { deletePost = null }, { viewModel.onEvent(OfficialFeedUiEvent.DeletePost(post.id)); deletePost = null }) }
    if (liveOpen) slots.floatingPanel({ liveOpen = false }) { panelModifier, panelLandscape ->
        val items = state.posts.sortedWith(compareByDescending<OfficialPostItem> { it.likesCount }.thenByDescending { it.createdAt }).mapIndexed { index, post -> QuataLiveRankingItem(post.id, post.author.id, ranks[post.id]?.position ?: index + 1, post.title, post.author.displayName, post.author.displayName, post.author.avatarUrl, true, post.likesCount) }
        QuataLiveRankingPanelContent(items, panelLandscape, QuataLiveRankingStrings(strings.rank, strings.live, "${items.size}", strings.refresh, strings.live, strings.close, strings.readMore), slots.rankingAvatar, { liveOpen = false }, { id -> state.posts.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { scope.launch { pagerState.animateScrollToPage(it) } }; liveOpen = false }, panelModifier)
    }
    // Native media viewers are deliberately injected at the platform seam; this host only owns selection.
    mediaPost?.let { id -> state.posts.firstOrNull { it.id == id }?.let { post -> slots.mediaViewer(post) { mediaPost = null } } }
}

@Composable private fun OfficialHostFailure(message: String, retry: String, onRetry: () -> Unit, modifier: Modifier) = Box(modifier, contentAlignment = Alignment.Center) { TextButton(onRetry) { Text("$message · $retry") } }

internal fun officialSharePayload(post: OfficialPostItem) = SharePayload("${post.title}\n\n${post.summary.ifBlank { post.contentPlain }}\n\n${quataOfficialPostUrl(post.id)}", post.title)
