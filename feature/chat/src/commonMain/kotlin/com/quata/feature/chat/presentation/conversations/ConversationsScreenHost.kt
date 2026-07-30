package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.platform.ClipboardService
import com.quata.core.ui.components.*
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact
import kotlinx.coroutines.delay

/** The only cross-platform conversations root. Platform code supplies only boundary capabilities. */
@Composable
fun ConversationsScreenHost(
    viewModel: ConversationsViewModel,
    clipboardService: ClipboardService,
    strings: ConversationsScreenStrings,
    onOpenConversation: (String) -> Unit,
    onOpenFavorites: () -> Unit,
    padding: PaddingValues = PaddingValues(),
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null,
    remoteAvatar: @Composable (name: String, avatarUrl: String?, stableId: String, modifier: Modifier) -> Unit = { name, _, stableId, modifier -> QuataAvatarFallback(name, stableId, modifier) },
    avatarLoadingOverlay: @Composable (Boolean, Modifier) -> Unit = { _, _ -> },
    inviteContactsEnabled: Boolean = false,
    onRequestInviteContactsPermission: (() -> Unit)? = null,
    inviteSheet: (@Composable (ChatInviteContact, ClipboardService, () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(conversationsCurrentTimeMillis()) }
    val visible = remember(state.conversations, state.usersById, state.messagesByConversation, query, strings) {
        filterConversations(state.conversations, query, state.usersById, state.messagesByConversation, strings)
    }
    val rows = remember(visible, state.messagesByConversation, now, strings) {
        visible.map { conversation ->
            val raw = state.messagesByConversation[conversation.id].orEmpty().lastOrNull()?.text ?: conversation.lastMessagePreview
            ConversationListRow(conversation, conversation.conversationDisplayTitle(strings.emergencyTitle), strings.localizePreview(raw), strings.relativeTime(conversation.updatedAt, conversation.updatedAtMillis, now))
        }
    }
    val contentPadding = if (rememberQuataWindowLayoutInfo().isLandscape) PaddingValues(8.dp, 18.dp, 18.dp, 18.dp) else PaddingValues(18.dp)
    QuataScreen(padding) {
        Box(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(contentPadding)) {
                ConversationsListHeaderContent(strings.title, query, strings.searchPlaceholder, { query = it }, trailingAction = {
                    CompactIconButton(onClick = onOpenFavorites) { CompactIcon(Icons.Filled.Star, strings.favorites, tint = QuataOrange) }
                })
                Spacer(Modifier.height(16.dp))
                ConversationsListContent(rows, state.isLoading && state.conversations.isEmpty(), avatar = { row ->
                    CommonConversationAvatar(row.conversation, state, strings, remoteAvatar, avatarLoadingOverlay, openingProfileUserId, onOpenUserProfile)
                }, onOpenConversation = { onOpenConversation(it.conversation.id) }, modifier = Modifier.weight(1f), emptyContent = {
                    (state.error ?: strings.empty)?.let { text -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { Text(text) } }
                })
            }
            state.pendingDeletedConversation?.let { pending ->
                ConversationDeleteUndoContent(pending.conversationDisplayTitle(strings.emergencyTitle), strings.undo, { viewModel.onEvent(ConversationsUiEvent.RestoreDeletedConversation) }, Modifier.align(Alignment.BottomStart).fillMaxWidth(.72f).padding(18.dp))
            }
            NewConversationFabContent(strings.newChat, { viewModel.openNewConversationPicker() }, Modifier.align(Alignment.BottomEnd).padding(18.dp))
        }
    }
    if (state.isNewConversationPickerOpen) {
        ConversationCandidatePickerDialogContent(
            state, clipboardService, strings.picker, viewModel::onCandidateQueryChanged, viewModel::loadMoreConversationCandidates,
            { viewModel.openCandidateConversation(it, onOpenConversation) }, viewModel::closeNewConversationPicker,
            panelHost = { content -> QuataStandardFloatingPanelContent(viewModel::closeNewConversationPicker, template = quataTheme(), content = content) },
            candidateAvatar = { candidate, modifier -> remoteAvatar(candidate.displayName, candidate.avatarUrl, candidate.profileId, modifier) },
            inviteAvatar = { contact, modifier -> QuataAvatarFallback(contact.displayName, contact.id, modifier) },
            inviteSheet = inviteSheet, inviteContactsEnabled = inviteContactsEnabled,
            onRequestInviteContactsPermission = onRequestInviteContactsPermission, title = strings.newChatTitle,
            actionIcon = Icons.Filled.ChatBubble, actionContentDescription = strings.newChat,
            confirmIcon = Icons.AutoMirrored.Filled.Send, confirmContentDescription = strings.newChat,
        )
    }
    LaunchedEffect(state.pendingDeletedConversation?.id) { if (state.pendingDeletedConversation != null) { delay(4_000); viewModel.onEvent(ConversationsUiEvent.FinalizeDeletedConversation) } }
    LaunchedEffect(Unit) { while (true) { delay(1_000); now = conversationsCurrentTimeMillis() } }
}

@Composable private fun CommonConversationAvatar(item: com.quata.core.model.Conversation, state: ConversationsUiState, strings: ConversationsScreenStrings, remote: @Composable (String, String?, String, Modifier) -> Unit, loadingOverlay: @Composable (Boolean, Modifier) -> Unit, opening: String?, onProfile: (String) -> Unit) {
    val template = quataTheme(); val other = item.participantIds.firstOrNull { it != state.currentUser?.id }?.let(state.usersById::get)
    Box(Modifier.size(52.dp), Alignment.Center) {
        when { item.isEmergency -> Box(Modifier.size(46.dp).clip(CircleShape).background(template.colors.sosSurface).border(1.dp, template.colors.accent.copy(.45f), CircleShape), Alignment.Center) { Text(strings.sosLabel, color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = template.textSizes.caption) }
            item.isGroup -> remote(item.conversationDisplayTitle(strings.emergencyTitle), item.avatarUrl, item.id, Modifier.size(46.dp))
            other != null -> Box(Modifier.size(46.dp).clip(CircleShape).clickable { onProfile(other.id) }, Alignment.Center) { remote(other.displayName, other.avatarUrl, other.id, Modifier.matchParentSize()); loadingOverlay(opening == other.id, Modifier.matchParentSize()) }
            else -> QuataAvatarFallback(item.conversationDisplayTitle(strings.emergencyTitle), item.id, Modifier.size(46.dp)) }
        if (item.isMuted) Box(Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(template.colors.surfaceRaised).border(1.dp, template.colors.divider, CircleShape), Alignment.Center) { Icon(Icons.Filled.NotificationsOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Kept small and deterministic for all non-Android targets. */
internal expect fun conversationsCurrentTimeMillis(): Long
internal expect fun parseConversationUpdatedAtMillis(value: String, nowMillis: Long): Long?
