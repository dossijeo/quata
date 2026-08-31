package com.quata.feature.feed.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.navigation.quataPostUrl
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.SharePayload
import com.quata.core.text.cleanTextCanvasSeedBody
import com.quata.core.text.extractPostMeta
import com.quata.core.text.parsePostShortcodeContent
import com.quata.core.ui.components.QuataCommentInputContent
import com.quata.core.ui.components.QuataCommentInputStrings
import com.quata.core.ui.components.QuataCommentRowContent
import com.quata.core.ui.components.QuataCommentRowStrings
import com.quata.core.ui.components.QuataCommentsPanelHeaderContent
import com.quata.core.ui.components.QuataCommentsPanelPortraitContent
import com.quata.core.ui.components.QuataCommentsPanelLandscapeContent
import com.quata.core.ui.components.QuataReplyTargetBannerContent
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataFeedPullRefreshIndicator
import com.quata.core.ui.components.rememberQuataFeedPullRefreshState
import com.quata.core.ui.components.QuataLiveRankingPanelContent
import com.quata.core.ui.components.QuataLiveRankingItem
import com.quata.core.ui.components.QuataPostDetailChromeContent
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
import com.quata.core.ui.components.QuataLiveRankingStrings
import com.quata.core.ui.components.CommunityEmojiCatalogState
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.CommunityEmojiPanelContent
import com.quata.core.ui.components.communityEmojiCatalogState
import com.quata.core.ui.components.dismissCommunityEmojiPanelOnOutsideTap
import com.quata.core.ui.components.rememberCommunityEmojiPanelDismissState
import com.quata.core.ui.components.trackCommunityEmojiPanelBounds
import com.quata.core.ui.components.trackCommunityEmojiTriggerBounds
import com.quata.core.ui.components.insertAtSelection
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.designsystem.translation.FangTranslatorTriggerContent
import com.quata.designsystem.translation.LocalQuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatorGateway
import com.quata.designsystem.translation.QuataTranslatorOverlayContent
import com.quata.designsystem.translation.QuataTranslatorStrings
import com.quata.designsystem.translation.quataTranslatorStringsForLanguage
import com.quata.designsystem.translation.quataTranslatableText
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.ui.graphics.Color
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
    val sharePostTitle: String = "Compartir publicaci\u00f3n",
    val shareUnavailable: String = "No se puede compartir esta publicación en este dispositivo.",
    val shareFailed: String = "No se pudo compartir la publicación.",
    val rank: String = "Ranking",
    val live: String = "LIVE",
    val publish: String = "Publicar",
    val report: String = "Reportar",
    val delete: String = "Eliminar",
    val deleteTitle: String = "Eliminar publicación",
    val deleteMessage: String = "Esta acción no se puede deshacer.",
    val reportSuccess: String = "Publicación reportada",
    val deleteSuccess: String = "Publicación eliminada",
    val liveTitle: String = "En directo", val liveSubtitle: String = "Publicaciones destacadas",
    val liveMonitored: @Composable (Int) -> String = { "Publicaciones monitorizadas: $it" }, val liveUpdated: String = "Actualizado", val liveOpenPost: String = "Abrir",
    val videoType: String = "Vídeo", val imageType: String = "Imagen", val textType: String = "Publicación",
    val cancel: String = "Cancelar",
    val close: String = "Cerrar",
    val commentPlaceholder: String = "Escribe un comentario",
    val translatorContentDescription: String = "Traductor Fang",
    val send: String = "Enviar",
    val reply: String = "Responder",
    val replyingTo: @Composable (String) -> String = { "Respondiendo a $it" }, val cancelReply: String = "Cancelar respuesta",
    val commentsTitle: String = "Comentarios", val commentsYou: String = "Tú", val moderationReport: String = "Reportar",
    val replyTo: (String) -> String = { "En respuesta a $it" },
    val showEmojis: String = "Mostrar emojis",
    val emojiLabels: CommunityEmojiLabels = CommunityEmojiLabels(),
    val locationLabel: @Composable (String) -> String = { location -> formatFeedLocationLabel(location) },
    val detailTitle: String = "Detalle de publicación",
    val detailBack: String = "Volver al feed",
)

const val FeedPostDetailChromeTestTag = "feed.detail.chrome"
const val FeedPostDetailBackTestTag = "feed.detail.back"

/** Shared location chip text; Android's localized resource intentionally uses the same red pin. */
fun formatFeedLocationLabel(location: String): String = "\uD83D\uDCCD $location"

/** Platform-only rendering and service hooks. The Feed state machine stays in commonMain. */
data class FeedScreenPlatformSlots(
    val media: @Composable BoxScope.(
        post: Post,
        isCurrent: Boolean,
        initialPositionMs: Long,
        onPositionChanged: (Long) -> Unit,
        isFeedMuted: Boolean,
        onFeedMuteChange: (Boolean) -> Unit,
    ) -> Unit,
    val avatar: @Composable (Post) -> Unit = {},
    val rankingAvatar: @Composable (QuataLiveRankingItem) -> Unit = {},
    /** Presence-aware avatar hooks; legacy hooks remain available to Android and old hosts. */
    val avatarWithPresence: @Composable (Post, Boolean?) -> Unit = { post, _ -> avatar(post) },
    val rankingAvatarWithPresence: @Composable (QuataLiveRankingItem, Boolean?) -> Unit = { item, _ -> rankingAvatar(item) },
    /** Receives the canonical public post URL and the platform's activity-sheet title. */
    val share: suspend (SharePayload) -> PlatformResult<Unit> = { PlatformResult.Unsupported },
    val message: (String) -> Unit = {},
    /** Keeps the shared Fang affordance while allowing Android to activate its overlay. */
    val commentsTranslatorTrigger: @Composable (String, Modifier, () -> Unit, Boolean) -> Unit = { contentDescription, modifier, onClick, enabled ->
        FangTranslatorTriggerContent(contentDescription = contentDescription, onClick = onClick, enabled = enabled, modifier = modifier)
    },
    val commentsTranslationGateway: QuataTranslatorGateway? = null,
    val commentsTranslatorStrings: QuataTranslatorStrings = quataTranslatorStringsForLanguage(null),
    /** Registers the exact shared row text with the platform translator overlay. */
    val commentRowModifier: (PostComment, String) -> Modifier = { comment, displayText ->
        Modifier.quataTranslatableText(
            id = "feed-comment:${comment.id}",
            text = comment.message,
            displayText = displayText,
        )
    },
    /** Web/iOS use the common Snackbar surface; Android keeps its native Toast adapter. */
    val showComposeMessage: Boolean = false,
    val standardFloatingPanel: @Composable (
        onDismiss: () -> Unit,
        content: @Composable (Modifier, Boolean) -> Unit,
    ) -> Unit = { dismiss, content -> QuataStandardFloatingPanelContent(onDismiss = dismiss, content = content) },
    val communityEmojiCatalog: (CommunityEmojiLabels, (() -> Unit)?) -> CommunityEmojiCatalogState = { labels, onRetry ->
        communityEmojiCatalogState(labels, onRetry = onRetry)
    },
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
    presence: FeedUserPresence? = null,
    currentUserId: String? = null,
    focusedPostId: String? = null,
    feedResetToken: Int = 0,
    networkReconnectToken: Long = 0L,
    // The Feed must measure its own Compose container.  iOS creates this common host directly,
    // so a static default here silently locked it to the portrait variant after rotation.
    // Platform callers can still override the value when their navigation shell owns it.
    isLandscape: Boolean = rememberQuataWindowLayoutInfo().isLandscape,
    strings: FeedScreenStrings = FeedScreenStrings(),
    onFocusedPostHandled: () -> Unit = {},
    onBackFromFocusedPost: (() -> Unit)? = null,
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
    val onlineProfileIds by (presence?.onlineProfileIds ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptySet()) }).collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    fun showMessage(message: String) {
        slots.message(message)
        if (slots.showComposeMessage) scope.launch { snackbarHostState.showSnackbar(message) }
    }
    var commentsPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletionPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeletedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var liveOpen by rememberSaveable { mutableStateOf(false) }
    // Audio is a Feed-wide product preference. Platform decoders consume this state; they do not
    // own one independent mute flag per renderer or per route host.
    var isFeedMuted by rememberSaveable { mutableStateOf(false) }
    var emojiCatalogRetryToken by rememberSaveable { mutableStateOf(0) }
    var handledFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var retainedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasAppliedRetainedPost by remember { mutableStateOf(retainedPostId == null) }
    var handledReset by rememberSaveable { mutableStateOf(feedResetToken) }
    val videoPositions = remember { mutableMapOf<String, Long>() }
    val pagerState = rememberPagerState(pageCount = { state.posts.size })
    val effectiveCurrentUserId = currentUserId ?: state.currentUser?.id
    val canParticipate = effectiveCurrentUserId != null
    val ranks = remember(state.posts) { calculateFeedRanking(state.posts) }
    LaunchedEffect(state.posts, presence) {
        // Ranking rows are derived from these posts, so this also covers their author avatars.
        presence?.observeProfiles(state.posts.map { it.author.id })
    }
    DisposableEffect(presence) {
        presence?.setForeground(true)
        // The composition can leave and re-enter the Feed while the platform owner remains alive.
        // Pause here; the app owner releases transport/listeners through close().
        onDispose { presence?.setForeground(false) }
    }
    val canPullRefresh = pagerState.currentPage == 0 && !state.isRefreshing && commentsPostId == null && !liveOpen
    val pullRefreshState = rememberQuataFeedPullRefreshState(
        enabled = canPullRefresh,
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.onEvent(FeedUiEvent.Refresh) },
    )

    LaunchedEffect(commentsPostId, isLandscape) { onCommentsVisibilityChanged(commentsPostId != null && isLandscape) }
    DisposableEffect(Unit) { onDispose { onCommentsVisibilityChanged(false) } }

    LaunchedEffect(focusedPostId) {
        if (focusedPostId != null && focusedPostId != handledFocus) viewModel.onEvent(FeedUiEvent.FocusPost(focusedPostId))
    }
    LaunchedEffect(networkReconnectToken) {
        if (networkReconnectToken != 0L) viewModel.onEvent(FeedUiEvent.Refresh)
    }
    LaunchedEffect(state.posts, pendingDeletedPostId) {
        val deletedId = pendingDeletedPostId ?: return@LaunchedEffect
        if (state.posts.none { it.id == deletedId }) {
            showMessage(strings.deleteSuccess)
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

    Column(modifier.fillMaxSize()) {
        val detailPost = focusedPostId?.let { id -> state.posts.firstOrNull { it.id == id } }
        if (focusedPostId != null && onBackFromFocusedPost != null) {
            QuataPostDetailChromeContent(
                title = strings.detailTitle,
                subtitle = detailPost?.author?.displayName,
                backContentDescription = strings.detailBack,
                rootTestTag = FeedPostDetailChromeTestTag,
                backTestTag = FeedPostDetailBackTestTag,
                onBack = onBackFromFocusedPost,
            )
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().weight(1f)) {
            when {
                state.error != null && state.posts.isEmpty() -> FeedStatusContent(state.error ?: strings.loadingError, strings.retry, { viewModel.onEvent(FeedUiEvent.Refresh) }, Modifier.fillMaxSize().padding(padding))
                state.posts.isEmpty() && !state.isLoading -> FeedStatusContent(strings.empty, strings.retry, { viewModel.onEvent(FeedUiEvent.Refresh) }, Modifier.fillMaxSize().padding(padding))
                else -> FeedPagerViewportContent(padding, Modifier.fillMaxSize().nestedScroll(pullRefreshState.nestedScrollConnection)) {
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
                            video = {
                                slots.media(
                                    this,
                                    post,
                                    isCurrent,
                                    post.videoUrl?.let { videoPositions[it] } ?: 0L,
                                    { position -> post.videoUrl?.let { videoPositions[it] = position } },
                                    isFeedMuted,
                                    { isFeedMuted = it },
                                )
                            },
                            image = {
                                slots.media(this, post, isCurrent, 0L, {}, isFeedMuted) {
                                    isFeedMuted = it
                                }
                            },
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
                    avatar = { slots.avatarWithPresence(post, presence?.let { post.author.id in onlineProfileIds }) },
                    onOpenComments = { commentsPostId = post.id },
                    onOpenLive = { liveOpen = true },
                    onLike = { if (canParticipate) viewModel.onEvent(FeedUiEvent.ToggleLike(post.id)) else onAuthRequired() },
                    onDelete = { deletionPostId = post.id },
                    onShare = {
                        scope.launch {
                            feedShareResultMessage(
                                result = slots.share(feedSharePayload(post, strings.sharePostTitle)),
                                strings = strings,
                            )?.let(::showMessage)
                        }
                    },
                    onReport = {
                        if (post.isReportedByCurrentUser) Unit
                        else if (canParticipate) { viewModel.onEvent(FeedUiEvent.ReportPost(post.id)); showMessage(strings.reportSuccess) }
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
            if (slots.showComposeMessage) SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }

    state.posts.firstOrNull { it.id == commentsPostId }?.let { post ->
        FeedCommentsDialog(
            slots = slots,
            post = post,
            canParticipate = canParticipate,
            strings = strings,
            onAuthRequired = onAuthRequired,
            onReportComment = onReportComment,
            onOpenUserProfile = { profileId ->
                commentsPostId = null
                onOpenUserProfile(profileId)
            },
            onAddComment = { viewModel.onEvent(FeedUiEvent.AddComment(post.id, it)) },
            commentErrorMessage = state.commentErrorsByPostId[post.id],
            commentErrorsByCommentId = state.commentErrorsByCommentId,
            confirmedCommentIds = state.confirmedCommentIds,
            emojiCatalogState = {
                key(emojiCatalogRetryToken) {
                    slots.communityEmojiCatalog(strings.emojiLabels) { emojiCatalogRetryToken += 1 }
                }
            },
            onConfirmedCommentConsumed = { viewModel.onEvent(FeedUiEvent.ConfirmedCommentConsumed(it)) },
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
    if (liveOpen) slots.standardFloatingPanel({ liveOpen = false }) { panelModifier, panelLandscape ->
        Surface(panelModifier) {
            FeedLiveRankingDialogContent(
                posts = state.posts,
                rankForPost = { ranks[it.id] ?: 1 },
                postTypeLabel = { if (it.videoUrl != null) strings.videoType else if (it.imageUrl != null) strings.imageType else strings.textType },
                panel = { items, dismiss, open -> QuataLiveRankingPanelContent(items, panelLandscape, QuataLiveRankingStrings(strings.liveTitle, strings.liveSubtitle, strings.liveMonitored(items.size), strings.liveUpdated, strings.live, strings.close, strings.liveOpenPost), { item -> slots.rankingAvatarWithPresence(item, presence?.let { item.profileId in onlineProfileIds }) }, dismiss, open) },
                onDismiss = { liveOpen = false },
                onOpenPost = { post ->
                    val index = state.posts.indexOf(post)
                    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
                    liveOpen = false
                },
            )
        }
    }
}

/** Keep every platform action on the exact public link consumed by Android's share sheet. */
internal fun feedSharePayload(post: Post, title: String): SharePayload =
    SharePayload(text = quataPostUrl(post.id), title = title)

/** A cancelled native share sheet is not an error; unavailable/failing adapters must be visible. */
internal fun feedShareResultMessage(
    result: PlatformResult<Unit>,
    strings: FeedScreenStrings,
): String? = when (result) {
    is PlatformResult.Success, PlatformResult.Cancelled -> null
    PlatformResult.Unsupported -> strings.shareUnavailable
    is PlatformResult.Failure -> strings.shareFailed
}

@Composable
@OptIn(kotlin.time.ExperimentalTime::class)
private fun FeedCommentsDialog(
    slots: FeedScreenPlatformSlots,
    post: Post,
    canParticipate: Boolean,
    strings: FeedScreenStrings,
    onAuthRequired: () -> Unit,
    onReportComment: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onAddComment: (PostComment) -> Unit,
    commentErrorMessage: String?,
    commentErrorsByCommentId: Map<String, String>,
    confirmedCommentIds: Set<String>,
    emojiCatalogState: @Composable () -> CommunityEmojiCatalogState,
    onConfirmedCommentConsumed: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(post.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var replyTo by remember(post.id) { mutableStateOf<PostComment?>(null) }
    var pendingDraft by remember(post.id) { mutableStateOf<TextFieldValue?>(null) }
    var pendingReplyTo by remember(post.id) { mutableStateOf<PostComment?>(null) }
    var pendingCommentId by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var isEmojiPickerVisible by rememberSaveable(post.id) { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val emojiDismissState = rememberCommunityEmojiPanelDismissState { isEmojiPickerVisible = false }
    val emojiGridMaxHeight = if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) 168.dp else 220.dp
    fun setEmojiPickerVisible(visible: Boolean) {
        isEmojiPickerVisible = visible
        if (visible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    var shouldScrollToCommentsEnd by remember(post.id) { mutableStateOf(true) }
    val commentsListState = rememberLazyListState()
    val inheritedTranslatorRegistry = LocalQuataTranslatableTextRegistry.current
    val translatorRegistry = inheritedTranslatorRegistry ?: remember(post.id) { QuataTranslatableTextRegistry() }
    var translatorActive by rememberSaveable(post.id) { mutableStateOf(false) }
    val translatorGateway = slots.commentsTranslationGateway
    val translatorEnabled = translatorGateway != null && translatorRegistry.visibleBoxes.isNotEmpty()
    fun openTranslator() {
        if (translatorEnabled) translatorActive = true
    }
    LaunchedEffect(post.id, post.comments.size, shouldScrollToCommentsEnd) {
        if (shouldScrollToCommentsEnd) { delay(260); commentsListState.animateScrollToItem(post.comments.size); shouldScrollToCommentsEnd = false }
    }
    val pendingCommentErrorMessage = pendingCommentId?.let(commentErrorsByCommentId::get)
    val visibleCommentErrorMessage = if (pendingCommentId != null) pendingCommentErrorMessage else commentErrorMessage
    LaunchedEffect(pendingCommentErrorMessage, post.comments, pendingCommentId, confirmedCommentIds) {
        val pendingId = pendingCommentId ?: return@LaunchedEffect
        if (pendingCommentErrorMessage != null) {
            pendingDraft?.let { sentDraft ->
                draft = sentDraft
            }
            pendingCommentId = null
            pendingDraft = null
            pendingReplyTo = null
            return@LaunchedEffect
        }
        val confirmed = pendingId in confirmedCommentIds
        if (post.comments.none { it.id == pendingId } || confirmed) {
            pendingDraft?.let { sentDraft ->
                if (draft.text.trim() == sentDraft.text.trim()) draft = TextFieldValue()
            }
            if (replyTo?.id == pendingReplyTo?.id) replyTo = null
            if (confirmed) onConfirmedCommentConsumed(pendingId)
            pendingCommentId = null
            pendingDraft = null
            pendingReplyTo = null
        }
    }
    CompositionLocalProvider(LocalQuataTranslatableTextRegistry provides translatorRegistry) {
    slots.standardFloatingPanel(onDismiss) { panelModifier, landscape ->
        if (!landscape) QuataCommentsPanelPortraitContent(
                header = { QuataCommentsPanelHeaderContent(strings.commentsTitle, post.comments.size, { modifier -> slots.commentsTranslatorTrigger(strings.translatorContentDescription, modifier, ::openTranslator, translatorEnabled) }) },
                comments = { modifier -> LazyColumn(modifier.heightIn(min = 180.dp), state = commentsListState, contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { items(post.comments, key = { it.id }) { comment -> FeedCommentRow(comment, strings, slots.commentRowModifier, onOpenUserProfile, { replyTo = comment }, { if (canParticipate) onReportComment(comment.id) else onAuthRequired() }) }; item { Spacer(Modifier.height(24.dp)) } } },
                replyTarget = replyTo?.let {
                    {
                        QuataReplyTargetBannerContent(
                            comment = it,
                            replyingTo = strings.replyingTo(it.authorName),
                            cancelDescription = strings.cancelReply,
                            onClear = { replyTo = null },
                            targetTestTag = "feed.comments.replyTarget.${it.id}",
                            cancelTestTag = "feed.comments.replyCancel.${it.id}",
                        )
                    }
                },
                emojiPanel = if (isEmojiPickerVisible) {{ FeedCommunityEmojiPanel(emojiCatalogState(), strings, emojiGridMaxHeight, { draft = draft.insertAtSelection(it) }, Modifier.trackCommunityEmojiPanelBounds(emojiDismissState)) }} else null,
                input = { modifier -> QuataCommentInputContent(post.id, draft, replyTo, canParticipate, strings.commentsYou, QuataCommentInputStrings(strings.commentPlaceholder, strings.send), { nowCommentTimestamp() }, { CompactIconButton(onClick = { setEmojiPickerVisible(!isEmojiPickerVisible) }, enabled = pendingCommentId == null, modifier = Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState), testTag = "feed.comments.emoji", contentDescription = strings.showEmojis) { CompactIcon(Icons.Filled.InsertEmoticon, strings.showEmojis, tint = Color(0xFFFFC55C)) } }, { draft = it }, onAuthRequired, onAddComment, { comment -> pendingDraft = draft; pendingReplyTo = replyTo; pendingCommentId = comment.id; isEmojiPickerVisible = false; shouldScrollToCommentsEnd = true }, { if (isEmojiPickerVisible) setEmojiPickerVisible(false) }, modifier.fillMaxWidth(), isSending = pendingCommentId != null, inputTestTag = "feed.comments.input", sendTestTag = "feed.comments.send") },
                errorMessage = visibleCommentErrorMessage,
                errorTestTag = "feed.comments.error",
            modifier = panelModifier.dismissCommunityEmojiPanelOnOutsideTap(isEmojiPickerVisible, emojiDismissState),
        ) else QuataCommentsPanelLandscapeContent(
            header = { modifier -> QuataCommentsPanelHeaderContent(strings.commentsTitle, post.comments.size, { actionModifier -> slots.commentsTranslatorTrigger(strings.translatorContentDescription, actionModifier, ::openTranslator, translatorEnabled) }, modifier) },
            closeAction = { CompactIconButton(onClick = onDismiss) { CompactIcon(Icons.Filled.Close, strings.close) } },
            comments = { modifier -> LazyColumn(modifier, state = commentsListState, contentPadding = PaddingValues(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { items(post.comments, key = { it.id }) { comment -> FeedCommentRow(comment, strings, slots.commentRowModifier, onOpenUserProfile, { replyTo = comment }, { if (canParticipate) onReportComment(comment.id) else onAuthRequired() }) }; item { Spacer(Modifier.height(12.dp)) } } },
            replyTarget = replyTo?.let {
                {
                    QuataReplyTargetBannerContent(
                        comment = it,
                        replyingTo = strings.replyingTo(it.authorName),
                        cancelDescription = strings.cancelReply,
                        onClear = { replyTo = null },
                        targetTestTag = "feed.comments.replyTarget.${it.id}",
                        cancelTestTag = "feed.comments.replyCancel.${it.id}",
                    )
                }
            },
            input = { modifier -> QuataCommentInputContent(post.id, draft, replyTo, canParticipate, strings.commentsYou, QuataCommentInputStrings(strings.commentPlaceholder, strings.send), { nowCommentTimestamp() }, { CompactIconButton(onClick = { setEmojiPickerVisible(!isEmojiPickerVisible) }, enabled = pendingCommentId == null, modifier = Modifier.trackCommunityEmojiTriggerBounds(emojiDismissState), testTag = "feed.comments.emoji", contentDescription = strings.showEmojis) { CompactIcon(Icons.Filled.InsertEmoticon, strings.showEmojis, tint = Color(0xFFFFC55C)) } }, { draft = it }, onAuthRequired, onAddComment, { comment -> pendingDraft = draft; pendingReplyTo = replyTo; pendingCommentId = comment.id; isEmojiPickerVisible = false; shouldScrollToCommentsEnd = true }, { if (isEmojiPickerVisible) setEmojiPickerVisible(false) }, modifier, isSending = pendingCommentId != null, inputTestTag = "feed.comments.input", sendTestTag = "feed.comments.send") },
            emojiPanel = if (isEmojiPickerVisible) {{ FeedCommunityEmojiPanel(emojiCatalogState(), strings, emojiGridMaxHeight, { draft = draft.insertAtSelection(it) }, Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 84.dp, start = 24.dp).fillMaxWidth(0.62f).trackCommunityEmojiPanelBounds(emojiDismissState)) }} else null,
            errorMessage = visibleCommentErrorMessage,
            errorTestTag = "feed.comments.error",
            modifier = panelModifier.dismissCommunityEmojiPanelOnOutsideTap(isEmojiPickerVisible, emojiDismissState),
        )
    }
    translatorGateway?.let { gateway ->
        if (translatorActive) {
            QuataTranslatorOverlayContent(
                registry = translatorRegistry,
                gateway = gateway,
                strings = slots.commentsTranslatorStrings,
                onDismiss = { translatorActive = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    }
}

@Composable
private fun FeedCommunityEmojiPanel(
    catalogState: CommunityEmojiCatalogState,
    strings: FeedScreenStrings,
    gridMaxHeight: androidx.compose.ui.unit.Dp,
    onEmojiClick: (String) -> Unit,
    modifier: Modifier,
) {
    when (catalogState) {
        is CommunityEmojiCatalogState.Available -> CommunityEmojiPanelContent(
            catalogState.sections,
            onEmojiClick,
            modifier,
            gridMaxHeight = gridMaxHeight,
            emptyMessage = strings.emojiLabels.empty,
        )
        is CommunityEmojiCatalogState.Unavailable -> CommunityEmojiPanelContent(
            emptyList(),
            onEmojiClick,
            modifier,
            gridMaxHeight = gridMaxHeight,
            emptyMessage = strings.emojiLabels.empty,
            errorMessage = catalogState.message,
            retryLabel = strings.retry,
            onRetry = catalogState.onRetry,
        )
    }
}

@Composable
private fun FeedCommentRow(
    comment: PostComment,
    strings: FeedScreenStrings,
    rowModifier: (PostComment, String) -> Modifier,
    onOpenAuthorProfile: (String) -> Unit,
    onReply: () -> Unit,
    onReport: () -> Unit,
) {
    val timestamp = formatCommentTimestamp(comment.timestamp)
    val displayText = feedCommentTranslatorDisplayText(
        comment = comment,
        timestamp = timestamp,
        replyLabel = comment.replyToAuthorName?.let(strings.replyTo),
    )
    QuataCommentRowContent(
        comment = comment,
        timestamp = timestamp,
        strings = QuataCommentRowStrings(strings.replyTo, strings.moderationReport, strings.reply),
        modifier = rowModifier(comment, displayText),
        authorProfileTestTagPrefix = "feed.comments.author.",
        replyTestTagPrefix = "feed.comments.reply.",
        reportTestTagPrefix = "feed.comments.report.",
        replyQuoteTestTagPrefix = "feed.comments.replyTo.",
        onOpenAuthorProfile = onOpenAuthorProfile,
        onReply = onReply,
        onReport = onReport,
    )
}
