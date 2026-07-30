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
import com.quata.core.ui.components.QuataFeedOverflowActionButton
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
import com.quata.core.ui.components.rememberQuataFeedPullRefreshState
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.official.domain.calculateOfficialPostRanking
import kotlinx.coroutines.launch

/** Localized copy and platform-visible feedback for the shared Official product surface. */
class OfficialFeedScreenStrings(
    val empty: String,
    val create: String,
    val retry: String,
    val loadingError: String,
    val like: String,
    val comments: String,
    val share: String,
    val rank: String,
    val live: String,
    val delete: String,
    val close: String,
    val profile: String,
    val readMore: String,
    val refresh: String,
    val readMoreMoreInformation: String,
    val readMoreContinueReading: String,
    val readMoreDetails: String,
    val typeAnnouncement: String,
    val typeNews: String,
    val typeEvent: String,
    val typeUrgent: String,
    val officialAccountFallback: String,
    val deleteTitle: String,
    val deleteMessage: String,
    val confirm: String,
    val cancel: String,
    val deleted: String,
    val reportSent: String,
    val reportFailed: String,
    val shareUnavailable: String,
    val shareFailed: String,
) {
    constructor() : this(
        empty = "No hay comunicados oficiales disponibles.", create = "Crear comunicado",
        retry = "Reintentar", loadingError = "No se pudieron cargar los comunicados oficiales.",
        like = "Me gusta", comments = "Comentarios", share = "Compartir", rank = "Ranking",
        live = "LIVE", delete = "Eliminar", close = "Cerrar", profile = "Perfil",
        readMore = "Leer más", refresh = "Actualizar", readMoreMoreInformation = "Más información",
        readMoreContinueReading = "Seguir leyendo", readMoreDetails = "Detalles",
        typeAnnouncement = "Comunicado", typeNews = "Noticias", typeEvent = "Evento",
        typeUrgent = "Urgente", officialAccountFallback = "Cuenta oficial",
        deleteTitle = "Eliminar comunicado", deleteMessage = "Esta acción no se puede deshacer.",
        confirm = "Confirmar", cancel = "Cancelar", deleted = "Comunicado eliminado",
        reportSent = "Reporte enviado", reportFailed = "No se pudo enviar el reporte",
        shareUnavailable = "No se puede compartir este comunicado en este dispositivo.",
        shareFailed = "No se pudo compartir el comunicado.",
    )
}

fun defaultOfficialFeedScreenStrings(languageTag: String?): OfficialFeedScreenStrings = when (languageTag?.substringBefore('-')?.lowercase()) {
    "en" -> OfficialFeedScreenStrings(loadingError="Could not load official notices.",live="LIVE",readMoreMoreInformation="More information",readMoreContinueReading="Continue reading",readMoreDetails="Details",typeAnnouncement="Announcement",typeNews="News",typeEvent="Event",typeUrgent="Urgent",officialAccountFallback="Official account",deleteTitle="Delete notice",deleteMessage="This action cannot be undone.",confirm="Confirm",cancel="Cancel",deleted="Notice deleted",shareUnavailable="This notice cannot be shared on this device.",shareFailed="Could not share notice",empty="No official notices are available.",create="Create notice",retry="Retry",like="Like",comments="Comments",share="Share",rank="Ranking",delete="Delete",close="Close",profile="Profile",readMore="Read more",refresh="Refresh",reportSent="Report sent for review",reportFailed="Could not send report")
    "fr" -> OfficialFeedScreenStrings(loadingError="Impossible de charger les communiqués officiels.",live="DIRECT",readMoreMoreInformation="Plus d'informations",readMoreContinueReading="Continuer la lecture",readMoreDetails="Détails",typeAnnouncement="Communiqué",typeNews="Actualités",typeEvent="Événement",typeUrgent="Urgent",officialAccountFallback="Compte officiel",deleteTitle="Supprimer le communiqué",deleteMessage="Cette action est irréversible.",confirm="Confirmer",cancel="Annuler",deleted="Communiqué supprimé",shareUnavailable="Ce communiqué ne peut pas être partagé sur cet appareil.",shareFailed="Impossible de partager le communiqué",empty="Aucun communiqué officiel disponible.",create="Créer un communiqué",retry="Réessayer",like="J'aime",comments="Commentaires",share="Partager",rank="Classement",delete="Supprimer",close="Fermer",profile="Profil",readMore="Lire plus",refresh="Actualiser",reportSent="Signalement envoyé pour examen",reportFailed="Impossible d'envoyer le signalement")
    else -> OfficialFeedScreenStrings()
}

internal fun OfficialFeedScreenStrings.typeLabel(type: OfficialPostType): String = when (type) {
    OfficialPostType.Announcement -> typeAnnouncement
    OfficialPostType.News -> typeNews
    OfficialPostType.Event -> typeEvent
    OfficialPostType.Urgent -> typeUrgent
}

internal fun OfficialFeedScreenStrings.readMoreLabel(storedValue: String): String = when (storedValue.trim().lowercase()) {
    "more_information" -> readMoreMoreInformation
    "continue_reading" -> readMoreContinueReading
    "details" -> readMoreDetails
    else -> readMore
}

/** The only target-specific seams: rendering and externally-owned services/navigation. */
class OfficialFeedScreenPlatformSlots(
    val avatar: @Composable (OfficialPostItem, Modifier) -> Unit,
    val media: @Composable (OfficialPostItem, Modifier, () -> Unit) -> Unit,
    val article: @Composable (OfficialPostItem, Modifier) -> Unit,
    val mediaViewer: @Composable (OfficialPostItem, () -> Unit) -> Unit,
    val openUrl: (String) -> Unit,
    val share: suspend (SharePayload) -> PlatformResult<Unit>,
    val message: (String) -> Unit,
    val showComposeMessage: Boolean,
    val canCreateOfficialPost: Boolean,
    val rankingAvatar: @Composable (QuataLiveRankingItem) -> Unit,
    val floatingPanel: @Composable (onDismiss: () -> Unit, content: @Composable (Modifier, Boolean) -> Unit) -> Unit,
)

@Composable
fun OfficialDefaultFloatingPanel(
    onDismiss: () -> Unit,
    content: @Composable (Modifier, Boolean) -> Unit,
) = QuataStandardFloatingPanelContent(onDismiss = onDismiss, content = content)

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
    currentUserId: String?,
    focusedPostId: String?,
    strings: OfficialFeedScreenStrings,
    onFocusedPostHandled: () -> Unit,
    onAuthRequired: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: () -> Unit,
    modifier: Modifier,
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
        if (state.message == OfficialFeedMessages.CommentReported) { message(strings.reportSent); viewModel.onEvent(OfficialFeedUiEvent.ClearMessage) }
        if (state.message == OfficialFeedMessages.CommentReportFailed) { message(strings.reportFailed); viewModel.onEvent(OfficialFeedUiEvent.ClearMessage) }
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
                            post = post, typeLabel = strings.typeLabel(post.type), readMoreLabel = strings.readMoreLabel(post.readMoreLabel), isLandscape = windowInfo.isLandscape,
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
                            overflowAction = { overflowModifier -> QuataFeedOverflowActionButton(ranks[post.id]?.position ?: index + 1, strings.rank, strings.live, null, false, { liveOpen = true }, {}, overflowModifier) },
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
        OfficialPostDetailPanelContent(strings.readMoreLabel(post.readMoreLabel), strings.close, post.linkUrl, { readMorePost = null }, { slots.article(post, it) }, { OfficialAuthorHeaderContent(post.author.displayName, post.author.neighborhood, strings.officialAccountFallback, { slots.avatar(post, Modifier.size(58.dp)) }, it.clickable { onOpenUserProfile(post.author.id) }) }, post.mediaUrl?.takeIf(String::isNotBlank)?.let { { modifier -> slots.media(post, modifier) { mediaPost = post.id } } }, post.linkUrl?.let { link -> { modifier -> TextButton({ slots.openUrl(link) }, modifier) { Text(link) } } }, { modifier -> TextButton({ onOpenUserProfile(post.author.id) }, modifier) { Text(strings.profile) } })
    }
    OfficialCommentsPanelEntryContent(state.posts.firstOrNull { it.id == commentsPost }, state.posts, effectiveUserId, onAuthRequired, { postId, comment -> viewModel.onEvent(OfficialFeedUiEvent.AddComment(postId, comment)) }, { id -> viewModel.onEvent(OfficialFeedUiEvent.ReportComment(id)) }, { commentsPost = null }) { post, canParticipate, add, report, dismiss ->
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
