package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.model.Conversation
import com.quata.core.platform.ClipboardService
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay

/** Lifecycle-neutral state contract implemented by common and Android ViewModels. */
interface ConversationsScreenModel {
    val uiState: StateFlow<ConversationsUiState>
    fun onEvent(event: ConversationsUiEvent)
    fun openNewConversationPicker()
    fun closeNewConversationPicker()
    fun onCandidateQueryChanged(query: String)
    fun loadMoreConversationCandidates()
    fun loadMoreConversations() = Unit
    fun loadInviteContacts(contacts: List<ChatInviteContact>? = null)
    fun openCandidateConversation(candidate: ChatConversationCandidate, onOpened: (String) -> Unit)
    fun toggleNewConversationCandidate(candidate: ChatConversationCandidate)
    fun onNewGroupTitleChanged(title: String)
    fun openSelectedGroupConversation(onOpened: (String) -> Unit)
    fun close()
}

/** All text and formatting that varies by launcher is supplied at the boundary. */
const val ConversationFavoritesTestTag = "conversation.favorites"
const val ConversationNewTestTag = "conversation.new"
const val ConversationEmptyTestTag = "conversation.empty"
const val ConversationErrorTestTag = "conversation.error"
const val ConversationRetryTestTag = "conversation.retry"
const val ConversationLoadMoreTestTag = "conversation.loadMore"
const val ConversationPageErrorTestTag = "conversation.pageError"
const val ConversationPickerRootTestTag = "conversation.picker"
const val ConversationPickerSearchTestTag = "conversation.picker.search"
const val ConversationPickerCandidateTestTagPrefix = "conversation.picker.candidate."
const val ConversationPickerCandidateActionTestTagPrefix = "conversation.picker.candidate.action."
const val ConversationPickerConfirmTestTag = "conversation.picker.confirm"
const val ConversationPickerGroupTitleTestTag = "conversation.picker.groupTitle"
const val ConversationPickerDismissTestTag = "conversation.picker.dismiss"

data class ConversationsHostStrings(
    val title: String,
    val searchPlaceholder: String,
    val favoritesDescription: String,
    val newConversationDescription: String,
    val undoDelete: String,
    val empty: String,
    val retry: String,
    val loadMore: String,
    val loadingMore: String,
    val candidates: ConversationCandidatePickerStrings,
    val conversationTitle: (Conversation) -> String,
    val conversationPreview: (String) -> String,
    val relativeUpdatedAt: (Conversation, Long) -> String,
    val pickerTitle: String,
    val groupTitlePlaceholder: String,
    val createGroupDescription: String,
    val selectionSummary: (Int) -> String,
)

/** Explicit launcher locale catalogue; the host never manufactures copy of its own. */
/**
 * The single product composition for the conversation list.
 *
 * Launchers only supply platform services (avatars, permissions, invitation sheet and routing).
 * In particular this owns filtering, list-row construction, deletion timeout and candidate
 * pagination.  It deliberately does not create a repository or a second browser/iOS list.
 */
@Composable
fun ConversationsScreenHost(
    padding: PaddingValues,
    model: ConversationsScreenModel,
    clipboardService: ClipboardService,
    strings: ConversationsHostStrings,
    onOpenConversation: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null,
    onOpenFavorites: () -> Unit = {},
    contactsPermissionGranted: Boolean = false,
    onRequestInviteContactsPermission: (() -> Unit)? = null,
    autoRequestInviteContacts: Boolean = true,
    remoteConversationAvatar: @Composable (ConversationAvatarPresentation, Modifier) -> Unit,
    candidateAvatar: @Composable (ChatConversationCandidate, Modifier) -> Unit,
    inviteAvatar: @Composable (ChatInviteContact, Modifier) -> Unit,
    panelHost: @Composable (@Composable (Modifier, Boolean) -> Unit) -> Unit,
    inviteSheet: (@Composable (ChatInviteContact, ClipboardService, () -> Unit) -> Unit)? = null,
    nowMillisProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val viewModel = model
    val state by viewModel.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var contactsPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(nowMillisProvider()) }
    val visibleRows = remember(state.conversations, state.messagesByConversation, state.usersById, query, nowMillis, strings) {
        val cleanQuery = query.trim()
        state.conversations.asSequence()
            .filter { conversation ->
                if (cleanQuery.isBlank()) true else {
                    val rawPreview = state.messagesByConversation[conversation.id].orEmpty().lastOrNull()?.text
                        ?: conversation.lastMessagePreview
                    val participantNames = conversation.participantIds.mapNotNull(state.usersById::get)
                        .joinToString(" ") { it.displayName }
                    listOf(
                        strings.conversationTitle(conversation), conversation.title,
                        conversation.participantNames.joinToString(" "), participantNames,
                        strings.conversationPreview(rawPreview),
                    ).any { it.contains(cleanQuery, ignoreCase = true) }
                }
            }
            .map { conversation ->
                val rawPreview = state.messagesByConversation[conversation.id].orEmpty().lastOrNull()?.text
                    ?: conversation.lastMessagePreview
                ConversationListRow(
                    conversation = conversation,
                    title = strings.conversationTitle(conversation),
                    preview = strings.conversationPreview(rawPreview),
                    updatedAt = strings.relativeUpdatedAt(conversation, nowMillis),
                )
            }.toList()
    }
    val layout = rememberQuataWindowLayoutInfo()
    val contentPadding = if (layout.isLandscape) {
        PaddingValues(start = 8.dp, top = 18.dp, end = 18.dp, bottom = 18.dp)
    } else PaddingValues(18.dp)

    QuataScreen(padding) {
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                ConversationsListHeaderContent(
                    title = strings.title,
                    query = query,
                    searchPlaceholder = strings.searchPlaceholder,
                    onQueryChange = { query = it },
                    trailingAction = { ConversationsFavoritesAction(strings.favoritesDescription, onOpenFavorites) },
                )
                Spacer(Modifier.padding(8.dp))
                ConversationsListContent(
                    rows = visibleRows,
                    isLoading = state.isLoading && state.conversations.isEmpty(),
                    avatar = { row ->
                        ConversationAvatarContent(
                            presentation = resolveConversationAvatarPresentation(row.conversation, state.currentUser, state.usersById, strings.conversationTitle(row.conversation), openingProfileUserId),
                            onOpenUserProfile = onOpenUserProfile,
                            remoteAvatar = remoteConversationAvatar,
                        )
                    },
                    onOpenConversation = { row -> onOpenConversation(row.conversation.id) },
                    emptyContent = {
                        ConversationsStatusContent(
                            message = state.loadError ?: strings.empty,
                            retryLabel = strings.retry,
                            onRetry = { viewModel.onEvent(ConversationsUiEvent.Refresh) },
                            messageTag = if (state.loadError == null) ConversationEmptyTestTag else ConversationErrorTestTag,
                            actionTag = ConversationRetryTestTag,
                        )
                    },
                    footer = if (state.conversationHasMore || state.conversationPageError != null) {
                        {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                state.conversationPageError?.let { error ->
                                    Text(error, modifier = Modifier.semantics { testTag = ConversationPageErrorTestTag })
                                    Spacer(Modifier.height(8.dp))
                                }
                                QuataSecondaryButton(
                                    text = if (state.isConversationPageLoading) strings.loadingMore else strings.loadMore,
                                    enabled = !state.isConversationPageLoading,
                                    onClick = viewModel::loadMoreConversations,
                                    modifier = Modifier.semantics { testTag = ConversationLoadMoreTestTag },
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.weight(1f),
                )
            }
            state.pendingDeletedConversation?.let { conversation ->
                ConversationDeleteUndoContent(
                    title = strings.conversationTitle(conversation),
                    undoLabel = strings.undoDelete,
                    onUndo = { viewModel.onEvent(ConversationsUiEvent.RestoreDeletedConversation) },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(.72f).padding(18.dp),
                )
            }
            NewConversationFabContent(
                contentDescription = strings.newConversationDescription,
                onClick = viewModel::openNewConversationPicker,
                modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp).semantics {
                    testTag = ConversationNewTestTag
                    contentDescription = "$ConversationNewTestTag ${strings.newConversationDescription}"
                },
            )
        }
    }

    if (state.isNewConversationPickerOpen) {
        ConversationCandidatePickerDialogContent(
            state = state,
            clipboardService = clipboardService,
            strings = strings.candidates,
            onSearchChange = viewModel::onCandidateQueryChanged,
            onLoadMore = viewModel::loadMoreConversationCandidates,
            onOpenCandidate = { viewModel.openCandidateConversation(it, onOpenConversation) },
            onDismiss = viewModel::closeNewConversationPicker,
            panelHost = panelHost,
            candidateAvatar = candidateAvatar,
            inviteAvatar = inviteAvatar,
            inviteSheet = inviteSheet,
            inviteContactsEnabled = contactsPermissionGranted,
            onRequestInviteContactsPermission = onRequestInviteContactsPermission,
            title = strings.pickerTitle,
            actionIcon = Icons.Filled.ChatBubble,
            actionContentDescription = strings.newConversationDescription,
            confirmContentDescription = strings.createGroupDescription,
            selectedCandidateIds = state.selectedNewConversationProfileIds,
            onToggleCandidate = viewModel::toggleNewConversationCandidate,
            onConfirmSelection = { viewModel.openSelectedGroupConversation(onOpenConversation) },
            confirmEnabled = state.selectedNewConversationProfileIds.size >= 2 && !state.isOpeningGroupConversation,
            selectionSummary = if (state.selectedNewConversationProfileIds.size < 2) strings.candidates.noneSelected else strings.selectionSummary(state.selectedNewConversationProfileIds.size),
            groupTitle = state.newGroupTitle,
            onGroupTitleChange = viewModel::onNewGroupTitleChanged,
            groupTitlePlaceholder = strings.groupTitlePlaceholder,
            groupTitleTestTag = ConversationPickerGroupTitleTestTag,
            rootTestTag = ConversationPickerRootTestTag,
            searchTestTag = ConversationPickerSearchTestTag,
            candidateTestTagPrefix = ConversationPickerCandidateTestTagPrefix,
            candidateActionTestTagPrefix = ConversationPickerCandidateActionTestTagPrefix,
            confirmTestTag = ConversationPickerConfirmTestTag,
            dismissTestTag = ConversationPickerDismissTestTag,
        )
    }

    LaunchedEffect(state.isNewConversationPickerOpen, contactsPermissionGranted, autoRequestInviteContacts) {
        if (!state.isNewConversationPickerOpen) return@LaunchedEffect
        if (contactsPermissionGranted) viewModel.loadInviteContacts()
        else if (autoRequestInviteContacts && !contactsPermissionRequested) {
            contactsPermissionRequested = true
            onRequestInviteContactsPermission?.invoke()
        }
    }
    LaunchedEffect(state.pendingDeletedConversation?.id) {
        if (state.pendingDeletedConversation != null) {
            delay(4_000L)
            viewModel.onEvent(ConversationsUiEvent.FinalizeDeletedConversation)
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(1_000L); nowMillis = nowMillisProvider() } }
}

@Composable
private fun ConversationsFavoritesAction(description: String, onOpenFavorites: () -> Unit) {
    CompactIconButton(
        onClick = onOpenFavorites,
        modifier = Modifier.semantics {
            testTag = ConversationFavoritesTestTag
            contentDescription = "$ConversationFavoritesTestTag $description"
        },
    ) {
        CompactIcon(Icons.Filled.Star, contentDescription = description, tint = QuataOrange)
    }
}
