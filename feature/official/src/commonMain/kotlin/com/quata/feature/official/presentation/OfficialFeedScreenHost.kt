package com.quata.feature.official.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.navigation.AuthenticationContinuationCoordinator
import com.quata.core.navigation.AuthenticationContinuationIntent
import com.quata.core.navigation.AuthenticationContinuationKind
import com.quata.core.navigation.PendingAuthenticationContinuation
import com.quata.core.navigation.quataOfficialPostUrl
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.DurableMediaPositionStore
import com.quata.core.platform.MediaFileExportDescriptor
import com.quata.core.platform.mediaFileExportDescriptorOrNull
import com.quata.core.platform.SharePayload
import com.quata.core.ui.components.QuataFeedPullRefreshIndicator
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.CommunityEmojiCatalogState
import com.quata.core.ui.components.QuataFeedOverflowActionButton
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.core.ui.components.QuataPostDetailChromeContent
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
import com.quata.core.ui.components.communityEmojiCatalogState
import com.quata.core.ui.components.rememberQuataFeedPullRefreshState
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.designsystem.translation.FangTranslatorTriggerContent
import com.quata.designsystem.translation.QuataTranslatorGateway
import com.quata.designsystem.translation.QuataTranslatorMessageAction
import com.quata.designsystem.translation.QuataTranslatorStrings
import com.quata.designsystem.translation.quataTranslatorStringsForLanguage
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialRepository
import com.quata.feature.official.domain.calculateOfficialPostRanking
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val commentPlaceholder: String = "Escribe un comentario…",
    val commentSend: String = "Enviar comentario",
    val commentReport: String = "Reportar",
    val commentReply: String = "Responder",
    val commentReplyingTo: (String) -> String = { "Respondiendo a $it" },
    val commentCancelReply: String = "Cancelar respuesta",
    val commentsYou: String = "Tú",
    val commentReplyTo: (String) -> String = { "↳ Respuesta a $it" },
    val showEmojis: String = "Mostrar emojis",
    val translatorContentDescription: String = "Traductor Fang",
    val emojiLabels: CommunityEmojiLabels = CommunityEmojiLabels(),
    val detailTitle: String = "Detalle de comunicado",
    val detailBack: String = "Volver a comunicados",
    val mediaPlaybackFailed: String = "No se pudo reproducir el vídeo.",
    val downloadMedia: String = "Descargar",
    val shareMediaFile: String = "Compartir archivo",
    val mediaExportFailed: String = "No se pudo exportar",
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

const val OfficialPostDetailChromeTestTag = "official.detail.chrome"
const val OfficialPostDetailBackTestTag = "official.detail.back"
const val OfficialFeedRootTestTag = "official-feed-common-root"
const val OfficialFeedStateTestTagPrefix = "official-feed-common-state"
const val OfficialFeedErrorMessageTestTag = "official-feed-error-message"
const val OfficialFeedRetryTestTag = "official-feed-retry"
const val OfficialVideoPositionStoragePrefix = "quata.official.video_positions.v1."

internal fun officialVideoPositionMediaId(postId: String, videoUrl: String): String =
    "$postId\u001f${videoUrl.trim()}"

fun officialMediaFileExportDescriptor(post: OfficialPostItem): MediaFileExportDescriptor? {
    val mediaType = post.mediaType ?: return null
    return mediaFileExportDescriptorOrNull(
        reference = post.mediaUrl,
        title = post.title,
        mimeType = when (mediaType) {
            OfficialMediaType.Video -> "video/mp4"
            OfficialMediaType.Image -> "image/jpeg"
        },
    )
}

fun defaultOfficialFeedScreenStrings(languageTag: String?): OfficialFeedScreenStrings = when (languageTag?.substringBefore('-')?.lowercase()) {
    "en" -> OfficialFeedScreenStrings(loadingError="Could not load official notices.",mediaPlaybackFailed="Could not play the video.",downloadMedia="Download",shareMediaFile="Share file",mediaExportFailed="Could not export",live="LIVE",readMoreMoreInformation="More information",readMoreContinueReading="Continue reading",readMoreDetails="Details",typeAnnouncement="Announcement",typeNews="News",typeEvent="Event",typeUrgent="Urgent",officialAccountFallback="Official account",deleteTitle="Delete notice",deleteMessage="This action cannot be undone.",confirm="Confirm",cancel="Cancel",deleted="Notice deleted",shareUnavailable="This notice cannot be shared on this device.",shareFailed="Could not share notice",empty="No official notices are available.",create="Create notice",retry="Retry",like="Like",comments="Comments",share="Share",rank="Ranking",delete="Delete",close="Close",profile="Profile",readMore="Read more",refresh="Refresh",reportSent="Report sent for review",reportFailed="Could not send report",commentPlaceholder="Write a comment…",commentSend="Send comment",commentReport="Report",commentReply="Reply",commentReplyingTo={ "Replying to $it" },commentCancelReply="Cancel reply",commentsYou="You",commentReplyTo={ "↳ Reply to $it" },showEmojis="Show emojis",translatorContentDescription="Fang translator",emojiLabels=CommunityEmojiLabels(recent="Recent",frequent="Frequent",gestures="Gestures",people="People",animalsNature="Animals and nature",foodDrink="Food and drink",objectsSymbols="Objects and symbols",flags="Flags",empty="No emojis available."))
    "fr" -> OfficialFeedScreenStrings(loadingError="Impossible de charger les communiqués officiels.",mediaPlaybackFailed="Impossible de lire la vidéo.",downloadMedia="Télécharger",shareMediaFile="Partager le fichier",mediaExportFailed="Impossible d'exporter",live="DIRECT",readMoreMoreInformation="Plus d'informations",readMoreContinueReading="Continuer la lecture",readMoreDetails="Détails",typeAnnouncement="Communiqué",typeNews="Actualités",typeEvent="Événement",typeUrgent="Urgent",officialAccountFallback="Compte officiel",deleteTitle="Supprimer le communiqué",deleteMessage="Cette action est irréversible.",confirm="Confirmer",cancel="Annuler",deleted="Communiqué supprimé",shareUnavailable="Ce communiqué ne peut pas être partagé sur cet appareil.",shareFailed="Impossible de partager le communiqué",empty="Aucun communiqué officiel disponible.",create="Créer un communiqué",retry="Réessayer",like="J'aime",comments="Commentaires",share="Partager",rank="Classement",delete="Supprimer",close="Fermer",profile="Profil",readMore="Lire plus",refresh="Actualiser",reportSent="Signalement envoyé pour examen",reportFailed="Impossible d'envoyer le signalement",commentPlaceholder="Écris un commentaire…",commentSend="Envoyer le commentaire",commentReport="Signaler",commentReply="Répondre",commentReplyingTo={ "Réponse à $it" },commentCancelReply="Annuler la réponse",commentsYou="Toi",commentReplyTo={ "↳ Réponse à $it" },showEmojis="Afficher les emojis",translatorContentDescription="Traducteur Fang",emojiLabels=CommunityEmojiLabels(recent="Récents",frequent="Fréquents",gestures="Gestes",people="Personnes",animalsNature="Animaux et nature",foodDrink="Cuisine et boissons",objectsSymbols="Objets et symboles",flags="Drapeaux",empty="Aucun emoji disponible."))
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
    val mediaViewer: @Composable (OfficialPostItem, Long, (Long) -> Unit, () -> Unit) -> Unit,
    val openUrl: (String) -> Unit,
    val share: suspend (SharePayload) -> PlatformResult<Unit>,
    val message: (String) -> Unit,
    val showComposeMessage: Boolean,
    val canCreateOfficialPost: Boolean,
    val rankingAvatar: @Composable (QuataLiveRankingItem) -> Unit,
    val commentsTranslatorTrigger: @Composable (String, Modifier, () -> Unit, Boolean) -> Unit = { contentDescription, modifier, onClick, enabled ->
        FangTranslatorTriggerContent(contentDescription = contentDescription, onClick = onClick, enabled = enabled, modifier = modifier)
    },
    val commentsTranslationGateway: QuataTranslatorGateway? = null,
    val commentsTranslatorStrings: QuataTranslatorStrings = quataTranslatorStringsForLanguage(null),
    val commentsTranslatorMessageAction: QuataTranslatorMessageAction? = null,
    val communityEmojiCatalog: (CommunityEmojiLabels, (() -> Unit)?) -> CommunityEmojiCatalogState = { labels, onRetry ->
        communityEmojiCatalogState(labels, onRetry = onRetry)
    },
    /** Optional platform diagnostics hook; product state and rendering stay owned by commonMain. */
    val onDetailPostResolved: (OfficialPostItem?) -> Unit = {},
    val exposeE2eStateSemantics: Boolean = false,
)

internal fun dispatchOfficialLiveSelection(
    focusedPostId: String?,
    selectedPostId: String,
    onFocusedDetail: (String) -> Unit,
    onFeedPager: (String) -> Unit,
) {
    if (focusedPostId != null) onFocusedDetail(selectedPostId) else onFeedPager(selectedPostId)
}

internal sealed interface OfficialRankingTargetAction {
    data class Scroll(val index: Int) : OfficialRankingTargetAction
    data object RequestLoad : OfficialRankingTargetAction
    data object WaitForLoad : OfficialRankingTargetAction
    data object ClearFailedTarget : OfficialRankingTargetAction
}

internal fun resolveOfficialRankingTarget(
    targetPostId: String,
    visiblePostIds: List<String>,
    loadState: OfficialFocusedPostLoad?,
): OfficialRankingTargetAction {
    val index = visiblePostIds.indexOf(targetPostId)
    if (index >= 0) return OfficialRankingTargetAction.Scroll(index)
    return when (loadState) {
        null -> OfficialRankingTargetAction.RequestLoad
        OfficialFocusedPostLoad.Loading -> OfficialRankingTargetAction.WaitForLoad
        OfficialFocusedPostLoad.Loaded,
        OfficialFocusedPostLoad.Failed,
        OfficialFocusedPostLoad.NotFound,
        -> OfficialRankingTargetAction.ClearFailedTarget
    }
}

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
    stateHolder: OfficialFeedStateHolder? = null,
    slots: OfficialFeedScreenPlatformSlots,
    currentUserId: String?,
    videoPositionStore: DurableMediaPositionStore? = null,
    initialCurrentUser: User? = null,
    focusedPostId: String?,
    strings: OfficialFeedScreenStrings,
    onFocusedPostHandled: () -> Unit,
    onFocusedPostChanged: (String) -> Unit = {},
    onBackFromFocusedPost: (() -> Unit)? = null,
    onAuthRequired: () -> Unit,
    onAuthenticationContinuationRequired: (AuthenticationContinuationIntent) -> Unit = { onAuthRequired() },
    authenticationContinuationCoordinator: AuthenticationContinuationCoordinator? = null,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: () -> Unit,
    onCurrentUserRoleResolved: (Boolean) -> Unit = {},
    modifier: Modifier,
) {
    val ownedViewModel = remember(repository, initialCurrentUser, stateHolder) {
        if (stateHolder == null) OfficialFeedViewModel(repository, initialCurrentUser = initialCurrentUser) else null
    }
    val viewModel = stateHolder ?: checkNotNull(ownedViewModel)
    DisposableEffect(ownedViewModel) { onDispose { ownedViewModel?.close() } }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val windowInfo = rememberQuataWindowLayoutInfo()
    var localFocusedPostId by rememberSaveable(focusedPostId) { mutableStateOf(focusedPostId) }
    val activeFocusedPostId = localFocusedPostId
    val visiblePosts = activeFocusedPostId?.let { target -> state.posts.filter { post -> post.id == target } } ?: state.posts
    val focusedPostPending = activeFocusedPostId != null && visiblePosts.isEmpty()
    val focusedPostLoad = activeFocusedPostId?.let { state.focusedPostLoads[it] }
    var readMorePost by rememberSaveable { mutableStateOf<String?>(null) }
    var commentsPost by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaPost by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaReturnReadMorePost by rememberSaveable { mutableStateOf<String?>(null) }
    var detailScrollAnchor by rememberSaveable(stateSaver = OfficialPostDetailScrollAnchor.Saver) {
        mutableStateOf(OfficialPostDetailScrollAnchor.Empty)
    }
    var deletePost by rememberSaveable { mutableStateOf<String?>(null) }
    var liveOpen by rememberSaveable { mutableStateOf(false) }
    var overflowPost by rememberSaveable { mutableStateOf<String?>(null) }
    var rankingTargetPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var emojiCatalogRetryToken by rememberSaveable { mutableStateOf(0) }
    var handledFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var retainedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var restored by remember { mutableStateOf(retainedPostId == null) }
    val pagerState = rememberPagerState(pageCount = { visiblePosts.size.coerceAtLeast(1) })
    val layoutDirection = LocalLayoutDirection.current
    val effectiveUserId = currentUserId ?: state.currentUser?.id
    val videoPositions = remember(videoPositionStore, effectiveUserId) { mutableStateMapOf<String, Long>() }
    val persistedVideoPositions = remember(videoPositionStore, effectiveUserId) { mutableStateMapOf<String, Long>() }
    var videoPositionsRestored by remember(videoPositionStore, effectiveUserId) {
        mutableStateOf(videoPositionStore == null)
    }
    LaunchedEffect(videoPositionStore, effectiveUserId) {
        val store = videoPositionStore ?: return@LaunchedEffect
        store.restore(effectiveUserId).forEach { (mediaId, positionMs) ->
            if (mediaId !in videoPositions) {
                videoPositions[mediaId] = positionMs
                persistedVideoPositions[mediaId] = positionMs
            }
        }
        videoPositionsRestored = true
    }
    fun updateVideoPosition(post: OfficialPostItem, positionMs: Long) {
        if (!videoPositionsRestored || post.mediaType != OfficialMediaType.Video) return
        val url = post.mediaUrl ?: return
        val mediaId = officialVideoPositionMediaId(post.id, url)
        val normalized = positionMs.coerceAtLeast(0L)
        videoPositions[mediaId] = normalized
        val lastPersisted = persistedVideoPositions[mediaId]
        if (lastPersisted == null || abs(normalized - lastPersisted) >= 1_000L) {
            persistedVideoPositions[mediaId] = normalized
            videoPositionStore?.let { store ->
                scope.launch { store.persistPosition(effectiveUserId, mediaId, normalized) }
            }
        }
    }
    val pendingAuthenticationContinuation by (
        authenticationContinuationCoordinator?.pending
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow<PendingAuthenticationContinuation?>(null) }
        ).collectAsState()
    val canPublish = state.currentUser?.isOfficial == true && slots.canCreateOfficialPost
    val ranks = remember(state.posts) { calculateOfficialPostRanking(state.posts) }
    val canPullRefresh = activeFocusedPostId == null && pagerState.currentPage == 0 && !state.isRefreshing && commentsPost == null && readMorePost == null && !liveOpen
    val pullRefresh = rememberQuataFeedPullRefreshState(canPullRefresh, state.isRefreshing) { viewModel.onEvent(OfficialFeedUiEvent.Refresh) }
    fun message(value: String) {
        slots.message(value)
        if (slots.showComposeMessage) scope.launch { snackbar.showSnackbar(value) }
    }
    fun create() { if (effectiveUserId == null) onAuthRequired() else onCreateOfficialPost() }
    LaunchedEffect(repository, currentUserId) {
        viewModel.refreshCurrentUser()
    }
    LaunchedEffect(state.isCurrentUserRoleResolved, state.currentUser?.isOfficial) {
        if (state.isCurrentUserRoleResolved) {
            onCurrentUserRoleResolved(state.currentUser?.isOfficial == true)
        }
    }

    LaunchedEffect(state.message) {
        if (state.message == OfficialFeedMessages.PostDeleted) { message(strings.deleted); viewModel.onEvent(OfficialFeedUiEvent.ClearMessage) }
        if (state.message == OfficialFeedMessages.CommentReported) { message(strings.reportSent); viewModel.onEvent(OfficialFeedUiEvent.ClearMessage) }
        if (state.message == OfficialFeedMessages.CommentReportFailed) { message(strings.reportFailed); viewModel.onEvent(OfficialFeedUiEvent.ClearMessage) }
    }
    LaunchedEffect(focusedPostId) {
        localFocusedPostId = focusedPostId
    }
    LaunchedEffect(activeFocusedPostId) {
        activeFocusedPostId?.takeIf { state.posts.none { post -> post.id == it } }?.let { viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(it)) }
    }
    LaunchedEffect(
        effectiveUserId,
        pendingAuthenticationContinuation?.requestId,
        state.posts,
        state.focusedPostLoads,
    ) {
        if (effectiveUserId == null) return@LaunchedEffect
        val coordinator = authenticationContinuationCoordinator ?: return@LaunchedEffect
        val pending = pendingAuthenticationContinuation ?: return@LaunchedEffect
        val intent = pending.intent
        fun resolvePost(postId: String): OfficialPostItem? {
            state.posts.firstOrNull { it.id == postId }?.let { return it }
            when (state.focusedPostLoads[postId]) {
                OfficialFocusedPostLoad.Loading, OfficialFocusedPostLoad.Failed -> Unit
                OfficialFocusedPostLoad.NotFound -> coordinator.clear(pending.requestId)
                OfficialFocusedPostLoad.Loaded, null -> viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(postId))
            }
            return null
        }
        when (intent.kind) {
            AuthenticationContinuationKind.OfficialTogglePostLike -> {
                val postId = intent.targetId ?: run {
                    coordinator.clear(pending.requestId)
                    return@LaunchedEffect
                }
                val desiredState = intent.desiredState ?: run {
                    coordinator.clear(pending.requestId)
                    return@LaunchedEffect
                }
                val post = resolvePost(postId) ?: return@LaunchedEffect
                if (post.isLikedByCurrentUser == desiredState) {
                    coordinator.clear(pending.requestId)
                } else if (coordinator.claim(pending.requestId) != null) {
                    viewModel.onEvent(OfficialFeedUiEvent.ToggleLike(postId))
                }
            }
            AuthenticationContinuationKind.OfficialAddComment -> {
                val postId = intent.targetId ?: run {
                    coordinator.clear(pending.requestId)
                    return@LaunchedEffect
                }
                val text = intent.text ?: run {
                    coordinator.clear(pending.requestId)
                    return@LaunchedEffect
                }
                val post = resolvePost(postId) ?: return@LaunchedEffect
                val replyTarget = intent.relatedId?.let { replyId -> post.comments.firstOrNull { it.id == replyId } }
                if (intent.relatedId != null && replyTarget == null) {
                    coordinator.clear(pending.requestId)
                    return@LaunchedEffect
                }
                commentsPost = postId
                if (coordinator.claim(pending.requestId) != null) {
                    val now = nowOfficialCommentTimestamp()
                    viewModel.onEvent(
                        OfficialFeedUiEvent.AddComment(
                            postId,
                            PostComment(
                                id = "local_${postId}_$now",
                                authorName = strings.commentsYou,
                                message = text,
                                timestamp = now,
                                replyToAuthorName = replyTarget?.authorName,
                                replyToMessage = replyTarget?.message,
                                replyToCommentId = replyTarget?.id,
                            ),
                        ),
                    )
                }
            }
            AuthenticationContinuationKind.OfficialReportComment -> {
                val commentId = intent.targetId ?: run {
                    coordinator.clear(pending.requestId)
                    return@LaunchedEffect
                }
                val postId = intent.relatedId ?: run {
                    coordinator.clear(pending.requestId)
                    return@LaunchedEffect
                }
                val post = resolvePost(postId) ?: return@LaunchedEffect
                if (post.comments.none { it.id == commentId }) {
                    coordinator.clear(pending.requestId)
                    return@LaunchedEffect
                }
                commentsPost = postId
                if (coordinator.claim(pending.requestId) != null) {
                    viewModel.onEvent(OfficialFeedUiEvent.ReportComment(commentId))
                }
            }
            else -> Unit
        }
    }
    LaunchedEffect(activeFocusedPostId, visiblePosts) {
        val target = activeFocusedPostId ?: return@LaunchedEffect
        val index = visiblePosts.indexOfFirst { it.id == target }
        if (target != handledFocus && index >= 0) { pagerState.scrollToPage(index); retainedPostId = target; restored = true; handledFocus = target; onFocusedPostHandled() }
    }
    LaunchedEffect(retainedPostId, state.posts, activeFocusedPostId) {
        val index = state.posts.indexOfFirst { it.id == retainedPostId }
        if (!restored && activeFocusedPostId == null && index >= 0) { pagerState.scrollToPage(index); restored = true }
    }
    LaunchedEffect(pagerState.currentPage, visiblePosts) { if (restored) visiblePosts.getOrNull(pagerState.currentPage)?.let { retainedPostId = it.id } }
    LaunchedEffect(rankingTargetPostId, visiblePosts, state.focusedPostLoads) {
        val target = rankingTargetPostId ?: return@LaunchedEffect
        when (
            val action = resolveOfficialRankingTarget(
                targetPostId = target,
                visiblePostIds = visiblePosts.map(OfficialPostItem::id),
                loadState = state.focusedPostLoads[target],
            )
        ) {
            is OfficialRankingTargetAction.Scroll -> {
                pagerState.scrollToPage(action.index)
                retainedPostId = target
                rankingTargetPostId = null
            }
            OfficialRankingTargetAction.RequestLoad ->
                viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(target))
            OfficialRankingTargetAction.WaitForLoad -> Unit
            OfficialRankingTargetAction.ClearFailedTarget -> {
                rankingTargetPostId = null
                message(strings.loadingError)
            }
        }
    }

    val showsDetailChrome = activeFocusedPostId != null && onBackFromFocusedPost != null
    val viewportPadding = if (showsDetailChrome) {
        PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            top = 0.dp,
            end = padding.calculateEndPadding(layoutDirection),
            bottom = padding.calculateBottomPadding(),
        )
    } else {
        padding
    }

    Column(
        modifier
            .fillMaxSize()
            .testTag(OfficialFeedRootTestTag)
            .let { tagged ->
                if (slots.exposeE2eStateSemantics) {
                    tagged.semantics {
                        val e2eState = officialFeedStateDescription(state)
                        stateDescription = e2eState
                        contentDescription = e2eState
                    }
                } else {
                    tagged
                }
            },
    ) {
        if (slots.exposeE2eStateSemantics) {
            val feedE2eState = officialFeedStateDescription(state)
            Box(
                Modifier
                    .size(1.dp)
                    .testTag("$OfficialFeedStateTestTagPrefix.created.${state.createdPostId ?: "none"}.count.${state.posts.size}")
                    .semantics {
                        stateDescription = feedE2eState
                        contentDescription = feedE2eState
                    },
            )
        }
        val detailPost = activeFocusedPostId?.let { id -> state.posts.firstOrNull { it.id == id } }
        LaunchedEffect(detailPost?.id, detailPost?.title, detailPost?.summary, detailPost?.contentPlain, detailPost?.linkUrl) {
            slots.onDetailPostResolved(detailPost)
        }
        if (showsDetailChrome) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            QuataPostDetailChromeContent(
                title = strings.detailTitle,
                subtitle = detailPost?.title,
                backContentDescription = strings.detailBack,
                rootTestTag = OfficialPostDetailChromeTestTag,
                backTestTag = OfficialPostDetailBackTestTag,
                onBack = {
                    localFocusedPostId = null
                    onBackFromFocusedPost?.invoke()
                },
            )
        }
        Box(Modifier.fillMaxSize().weight(1f)) {
            when {
                focusedPostPending && focusedPostLoad in setOf(OfficialFocusedPostLoad.NotFound, OfficialFocusedPostLoad.Failed) ->
                    OfficialHostFailure(
                        if (focusedPostLoad == OfficialFocusedPostLoad.NotFound) strings.empty else strings.loadingError,
                        strings.retry,
                        { activeFocusedPostId?.let { viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(it)) } },
                        Modifier.fillMaxSize(),
                    )
                activeFocusedPostId == null && state.error != null && state.posts.isEmpty() -> OfficialHostFailure(state.error ?: strings.loadingError, strings.retry, { viewModel.onEvent(OfficialFeedUiEvent.Refresh) }, Modifier.fillMaxSize())
                else -> OfficialFeedPagerContent(
                padding = viewportPadding,
                pagerState = pagerState,
                posts = visiblePosts,
                hasMoreOlderPosts = activeFocusedPostId == null && state.hasMoreOlderPosts && state.olderPageError == null,
                isLoadingOlder = state.isLoadingOlder,
                isInitialLoading = state.isLoading || focusedPostPending,
                onLoadOlder = { if (activeFocusedPostId == null) viewModel.onEvent(OfficialFeedUiEvent.LoadOlderPage) },
                emptyContent = { loading -> if (loading) OfficialLoadingContent(canPublish, OfficialStatusStrings(strings.empty, strings.create), ::create, Modifier.fillMaxSize()) else OfficialEmptyContent(canPublish, OfficialStatusStrings(strings.empty, strings.create), ::create, Modifier.fillMaxSize()) },
                pageContent = { index, post, _ ->
                    OfficialPagerPostPageContent(card = { cardModifier ->
                        OfficialPostCardContent(
                            post = post, typeLabel = strings.typeLabel(post.type), readMoreLabel = strings.readMoreLabel(post.readMoreLabel), isLandscape = windowInfo.isLandscape,
                            author = { authorModifier ->
                                OfficialAuthorHeaderContent(
                                    displayName = post.author.displayName,
                                    neighborhood = post.author.neighborhood,
                                    fallbackNeighborhood = strings.officialAccountFallback,
                                    authorProfileTestTag = officialAuthorAvatarTestTag(post.author.id),
                                    onOpenAuthorProfile = { onOpenUserProfile(post.author.id) },
                                    avatar = {
                                        slots.avatar(
                                            post,
                                            Modifier.size(58.dp),
                                        )
                                    },
                                    modifier = authorModifier,
                                )
                            },
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
                                    onOpenLive = {
                                        liveOpen = true
                                        viewModel.onEvent(OfficialFeedUiEvent.LoadCompleteRanking)
                                    },
                                    onLike = {
                                        if (effectiveUserId != null) viewModel.onEvent(OfficialFeedUiEvent.ToggleLike(post.id))
                                        else onAuthenticationContinuationRequired(
                                            officialAuthenticationContinuation(
                                                AuthenticationContinuationKind.OfficialTogglePostLike,
                                                targetId = post.id,
                                                desiredState = !post.isLikedByCurrentUser,
                                            ),
                                        )
                                    },
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
                            overflowAction = null,
                            onReadMore = { readMorePost = post.id }, modifier = cardModifier,
                        )
                    }, overlay = {
                        if (windowInfo.isLandscape) {
                            QuataFeedOverflowActionButton(
                                postRank = ranks[post.id]?.position ?: index + 1,
                                rankLabel = strings.rank,
                                liveLabel = strings.live,
                                reportLabel = null,
                                showReport = false,
                                expanded = overflowPost == post.id,
                                onExpandedChange = { expanded ->
                                    overflowPost = post.id.takeIf { expanded }
                                },
                                onOpenLive = {
                                    overflowPost = null
                                    liveOpen = true
                                    viewModel.onEvent(OfficialFeedUiEvent.LoadCompleteRanking)
                                },
                                onReport = {},
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 38.dp),
                            )
                        }
                    }, modifier = Modifier.fillMaxSize())
                },
                modifier = Modifier.fillMaxSize().nestedScroll(pullRefresh.nestedScrollConnection),
            ) {
                QuataFeedPullRefreshIndicator(pullRefresh, state.isRefreshing && pagerState.currentPage == 0, strings.refresh, Modifier.align(Alignment.TopCenter))
                if (state.isLoadingOlder) OfficialOlderPostsLoadingContent(Modifier.align(Alignment.BottomCenter))
                state.olderPageError?.let { failure ->
                    OfficialOlderPostsFailureContent(
                        message = failure.takeUnless { it == OfficialFeedMessages.OlderPageLoadFailed } ?: strings.loadingError,
                        retryLabel = strings.retry,
                        onRetry = { viewModel.onEvent(OfficialFeedUiEvent.RetryOlderPage) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
            if (slots.showComposeMessage) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
    state.posts.firstOrNull { it.id == readMorePost }?.let { post ->
        OfficialPostDetailPanelContent(
            postId = post.id,
            title = strings.readMoreLabel(post.readMoreLabel),
            closeLabel = strings.close,
            link = post.linkUrl,
            onDismiss = {
                readMorePost = null
                if (detailScrollAnchor.postId == post.id) {
                    detailScrollAnchor = OfficialPostDetailScrollAnchor.Empty
                }
            },
            articleContent = { slots.article(post, it) },
            author = {
                OfficialAuthorHeaderContent(
                    displayName = post.author.displayName,
                    neighborhood = post.author.neighborhood,
                    fallbackNeighborhood = strings.officialAccountFallback,
                    authorProfileTestTag = officialAuthorAvatarTestTag(post.author.id),
                    onOpenAuthorProfile = { onOpenUserProfile(post.author.id) },
                    avatar = {
                        slots.avatar(
                            post,
                            Modifier.size(58.dp),
                        )
                    },
                    modifier = it,
                )
            },
            media = post.mediaUrl?.takeIf(String::isNotBlank)?.let {
                { modifier ->
                    slots.media(post, modifier) {
                        mediaReturnReadMorePost = post.id
                        readMorePost = null
                        mediaPost = post.id
                    }
                }
            },
            resourceContent = post.linkUrl?.let { link -> { modifier -> TextButton({ slots.openUrl(link) }, modifier) { Text(link) } } },
            navigationContent = { modifier -> TextButton({ onOpenUserProfile(post.author.id) }, modifier) { Text(strings.profile) } },
            initialScrollAnchor = detailScrollAnchor.takeIf { it.postId == post.id },
            onScrollAnchorChanged = { detailScrollAnchor = it },
        )
    }
    OfficialCommentsPanelEntryContent(state.posts.firstOrNull { it.id == commentsPost }, state.posts, effectiveUserId, onAuthRequired, { postId, comment -> viewModel.onEvent(OfficialFeedUiEvent.AddComment(postId, comment)) }, { id -> viewModel.onEvent(OfficialFeedUiEvent.ReportComment(id)) }, { commentsPost = null }) { post, canParticipate, add, report, dismiss ->
        OfficialCommentsPanelContent(
            post = post,
            canParticipate = canParticipate,
            strings = OfficialCommentsStrings(
                title = strings.comments,
                close = strings.close,
                placeholder = strings.commentPlaceholder,
                send = strings.commentSend,
                report = strings.commentReport,
                reply = strings.commentReply,
                replyingTo = strings.commentReplyingTo,
                cancelReply = strings.commentCancelReply,
                commentsYou = strings.commentsYou,
                replyTo = strings.commentReplyTo,
                showEmojis = strings.showEmojis,
                retry = strings.retry,
                translatorContentDescription = strings.translatorContentDescription,
                emojiLabels = strings.emojiLabels,
            ),
            onAuthRequired = onAuthRequired,
            onAuthenticationContinuationRequired = onAuthenticationContinuationRequired,
            onAddComment = add,
            onReportComment = report,
            onOpenUserProfile = { profileId ->
                commentsPost = null
                onOpenUserProfile(profileId)
            },
            commentErrorMessage = state.commentErrorsByPostId[post.id],
            commentErrorsByCommentId = state.commentErrorsByCommentId,
            confirmedCommentIds = state.confirmedCommentIds,
            onConfirmedCommentConsumed = { viewModel.onEvent(OfficialFeedUiEvent.ConfirmedCommentConsumed(it)) },
            onDismiss = dismiss,
            translatorTrigger = slots.commentsTranslatorTrigger,
                translatorGateway = slots.commentsTranslationGateway,
                translatorStrings = slots.commentsTranslatorStrings,
                translatorMessageAction = slots.commentsTranslatorMessageAction,
                emojiCatalogState = {
                    key(emojiCatalogRetryToken) {
                        slots.communityEmojiCatalog(strings.emojiLabels) { emojiCatalogRetryToken += 1 }
                    }
                },
            )
    }
    state.posts.firstOrNull { it.id == deletePost }?.let { post -> OfficialDeleteConfirmationDialogContent(strings.deleteTitle, strings.deleteMessage, strings.confirm, strings.cancel, { deletePost = null }, { viewModel.onEvent(OfficialFeedUiEvent.DeletePost(post.id)); deletePost = null }) }
    if (liveOpen) QuataStandardFloatingPanelContent(onDismiss = { liveOpen = false }) { panelModifier, panelLandscape ->
        OfficialRankingLoadStateContent(
            isLoading = state.isLoadingRanking,
            error = state.rankingError,
            errorMessage = strings.loadingError,
            retryLabel = strings.retry,
            onRetry = { viewModel.onEvent(OfficialFeedUiEvent.LoadCompleteRanking) },
            modifier = panelModifier,
        ) { contentModifier ->
                val rankingPosts = state.rankingPosts ?: state.posts
                val items = rankingPosts
                    .sortedWith(compareByDescending<OfficialPostItem> { it.likesCount }.thenByDescending { it.createdAt })
                    .mapIndexed { index, post -> QuataLiveRankingItem(post.id, post.author.id, index + 1, post.title, post.author.displayName, post.author.displayName, post.author.avatarUrl, true, post.likesCount) }
                QuataLiveRankingPanelContent(
                    items,
                    panelLandscape,
                    QuataLiveRankingStrings(strings.rank, strings.live, "${items.size}", strings.refresh, strings.live, strings.close, strings.readMore),
                    slots.rankingAvatar,
                    { liveOpen = false },
                    { id ->
                        dispatchOfficialLiveSelection(
                            focusedPostId = activeFocusedPostId,
                            selectedPostId = id,
                            onFocusedDetail = { postId ->
                                localFocusedPostId = postId
                                onFocusedPostChanged(postId)
                            },
                            onFeedPager = { postId ->
                                viewModel.onEvent(OfficialFeedUiEvent.EnsurePostLoaded(postId))
                                rankingTargetPostId = postId
                            },
                        )
                        liveOpen = false
                    },
                    contentModifier,
                )
        }
    }
    // Native media viewers are deliberately injected at the platform seam; this host only owns selection.
    mediaPost?.let { id ->
        state.posts.firstOrNull { it.id == id }?.let { post ->
            val mediaId = post.mediaUrl?.takeIf { post.mediaType == OfficialMediaType.Video }?.let {
                officialVideoPositionMediaId(post.id, it)
            }
            key(videoPositionsRestored) {
                slots.mediaViewer(
                    post,
                    mediaId?.let { videoPositions[it] } ?: 0L,
                    { positionMs -> updateVideoPosition(post, positionMs) },
                ) {
                    mediaPost = null
                    mediaReturnReadMorePost?.let { readMorePost = it }
                    mediaReturnReadMorePost = null
                }
            }
        }
    }
}

internal fun officialAuthenticationContinuation(
    kind: AuthenticationContinuationKind,
    targetId: String? = null,
    relatedId: String? = null,
    text: String? = null,
    desiredState: Boolean? = null,
): AuthenticationContinuationIntent = AuthenticationContinuationIntent(
    kind = kind,
    originRoute = "official",
    targetId = targetId,
    relatedId = relatedId,
    text = text,
    desiredState = desiredState,
)

private fun officialFeedStateDescription(state: OfficialFeedUiState): String =
    buildString {
        append("{\"message\":")
        append(state.message?.let { "\"$it\"" } ?: "null")
        append(",\"createdPostId\":")
        append(state.createdPostId?.let { "\"$it\"" } ?: "null")
        append(",\"postCount\":")
        append(state.posts.size)
        append("}")
    }

@Composable
private fun OfficialHostFailure(
    message: String,
    retry: String,
    onRetry: () -> Unit,
    modifier: Modifier,
) = Box(modifier.testTag(OfficialFeedErrorMessageTestTag), contentAlignment = Alignment.Center) {
    TextButton(onRetry, Modifier.testTag(OfficialFeedRetryTestTag)) {
        Text("$message · $retry")
    }
}

internal fun officialSharePayload(post: OfficialPostItem) = SharePayload("${post.title}\n\n${post.summary.ifBlank { post.contentPlain }}\n\n${quataOfficialPostUrl(post.id)}", post.title)
