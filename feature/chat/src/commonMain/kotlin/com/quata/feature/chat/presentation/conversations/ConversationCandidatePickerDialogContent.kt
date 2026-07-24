package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.platform.ClipboardService
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataPermissionPromptCardContent
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact

data class ConversationCandidatePickerStrings(
    val searchPlaceholder: String,
    val noResults: String,
    val cancel: String,
    val contacts: String,
    val following: String,
    val followers: String,
    val recent: String,
    val otherNeighborhoods: String,
    val unknownNeighborhood: String,
    val inviteTitle: String,
    val invitePermission: String,
    val inviteAllow: String,
    val inviteAction: String,
    val noneSelected: String,
)

/** Platform panel and invite-channel rendering are injected; the picker state and list remain common. */
@Composable
fun ConversationCandidatePickerDialogContent(
    state: ConversationsUiState,
    clipboardService: ClipboardService,
    strings: ConversationCandidatePickerStrings,
    onSearchChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenCandidate: (ChatConversationCandidate) -> Unit,
    onDismiss: () -> Unit,
    panelHost: @Composable (@Composable (Modifier, Boolean) -> Unit) -> Unit,
    candidateAvatar: @Composable (ChatConversationCandidate, Modifier) -> Unit,
    inviteAvatar: @Composable (ChatInviteContact, Modifier) -> Unit,
    inviteSheet: (@Composable (ChatInviteContact, ClipboardService, () -> Unit) -> Unit)? = null,
    inviteContactsEnabled: Boolean = false,
    onRequestInviteContactsPermission: (() -> Unit)? = null,
    title: String,
    actionIcon: ImageVector,
    actionContentDescription: String,
    excludedProfileIds: Set<String> = emptySet(),
    selectedCandidateIds: Set<String> = emptySet(),
    onToggleCandidate: ((ChatConversationCandidate) -> Unit)? = null,
    onConfirmSelection: (() -> Unit)? = null,
    confirmEnabled: Boolean = selectedCandidateIds.isNotEmpty(),
    selectionSummary: String = "",
    confirmIcon: ImageVector = Icons.AutoMirrored.Filled.Send,
    confirmContentDescription: String,
) {
    val labels = CandidateDisplayLabels(strings.contacts, strings.following, strings.followers, strings.recent, strings.otherNeighborhoods, strings.unknownNeighborhood)
    val displayItems = remember(state.conversationCandidates, state.candidateActorNeighborhood, excludedProfileIds, labels) {
        buildCandidateDisplayItems(state.conversationCandidates.filterNot { it.profileId in excludedProfileIds }, state.candidateActorNeighborhood, labels)
    }
    val listState = rememberLazyListState()
    var pendingInvite by remember { mutableStateOf<ChatInviteContact?>(null) }
    LaunchedEffect(listState, displayItems.size, state.candidateHasMore, state.isCandidatePageLoading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (displayItems.isNotEmpty() && last >= displayItems.lastIndex - 5) onLoadMore()
        }
    }
    panelHost { panelModifier, isLandscape ->
        CandidatePickerPanel(
            state, strings, displayItems, listState, title, actionIcon, actionContentDescription,
            selectedCandidateIds, onToggleCandidate, onConfirmSelection, confirmEnabled, selectionSummary,
            confirmIcon, confirmContentDescription, onSearchChange, onOpenCandidate, onDismiss,
            candidateAvatar, inviteAvatar, inviteSheet != null, inviteContactsEnabled,
            onRequestInviteContactsPermission, { pendingInvite = it },
            panelModifier.padding(start = 20.dp, top = if (isLandscape) 18.dp else 10.dp, end = 20.dp, bottom = if (isLandscape) 18.dp else 24.dp),
        )
    }
    pendingInvite?.let { contact -> inviteSheet?.invoke(contact, clipboardService) { pendingInvite = null } }
}

@Composable
private fun CandidatePickerPanel(
    state: ConversationsUiState, strings: ConversationCandidatePickerStrings, displayItems: List<CandidateDisplayItem>, listState: LazyListState,
    title: String, actionIcon: ImageVector, actionDescription: String, selectedIds: Set<String>, onToggle: ((ChatConversationCandidate) -> Unit)?,
    onConfirm: (() -> Unit)?, confirmEnabled: Boolean, summary: String, confirmIcon: ImageVector, confirmDescription: String,
    onSearch: (String) -> Unit, onOpen: (ChatConversationCandidate) -> Unit, onDismiss: () -> Unit,
    avatar: @Composable (ChatConversationCandidate, Modifier) -> Unit, inviteAvatar: @Composable (ChatInviteContact, Modifier) -> Unit,
    showInvites: Boolean, inviteEnabled: Boolean, onRequestPermission: (() -> Unit)?, onInvite: (ChatInviteContact) -> Unit, modifier: Modifier,
) {
    val template = quataTheme()
    val filteredInvites = remember(state.inviteContacts, state.candidateQuery) { filterPickerInviteContacts(state.inviteContacts, state.candidateQuery) }
    val hasInvites = showInvites && !state.candidateHasMore && (filteredInvites.isNotEmpty() || state.isInviteContactsLoading || !inviteEnabled || state.inviteContactsError != null)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            CompactIconButton(onClick = onDismiss) { CompactIcon(Icons.Filled.Close, strings.cancel, tint = template.colors.textPrimary) }
        }
        Spacer(Modifier.padding(top = 10.dp))
        OutlinedTextField(state.candidateQuery, onSearch, placeholder = { Text(strings.searchPlaceholder) }, leadingIcon = { CompactIcon(Icons.Filled.Search, null, tint = template.colors.textSecondary) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        state.candidateError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.padding(top = 12.dp))
        when {
            state.isCandidateInitialLoading && state.conversationCandidates.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = template.colors.accent) }
            displayItems.isEmpty() && !hasInvites -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(strings.noResults, color = template.colors.textSecondary) }
            else -> LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(displayItems, key = { it.key }) { item ->
                    when (item) {
                        is CandidateDisplayItem.SectionHeader -> PickerSectionHeader(item.title)
                        is CandidateDisplayItem.NeighborhoodHeader -> PickerNeighborhoodHeader(item.title)
                        is CandidateDisplayItem.CandidateRow -> ConversationCandidateCardContent(item.candidate.displayName, item.candidate.neighborhood, state.openingCandidateProfileId == item.candidate.profileId, actionIcon, actionDescription, item.candidate.profileId in selectedIds, onToggle?.let { toggle -> { toggle(item.candidate) } }, { onOpen(item.candidate) }, { avatar(item.candidate, Modifier.size(48.dp)) })
                    }
                }
                if (state.isCandidatePageLoading) item("candidate-loading") { Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = template.colors.accent, modifier = Modifier.size(24.dp)) } }
                inviteItems(strings, state, filteredInvites, showInvites, inviteEnabled, onRequestPermission, inviteAvatar, onInvite)
            }
        }
        onConfirm?.let { confirm ->
            Spacer(Modifier.padding(top = 12.dp))
            Row(Modifier.fillMaxWidth().border(1.dp, template.colors.divider, RoundedCornerShape(18.dp)).background(template.colors.surface.copy(alpha = 0.76f), RoundedCornerShape(18.dp)).padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(summary.ifBlank { strings.noneSelected }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = template.colors.textPrimary.copy(alpha = if (selectedIds.isEmpty()) .54f else .94f), modifier = Modifier.weight(1f))
                Button(onClick = confirm, enabled = confirmEnabled, colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent), shape = CircleShape, modifier = Modifier.size(46.dp).compactButtonMinSize(), contentPadding = PaddingValues(0.dp)) { CompactIcon(confirmIcon, confirmDescription, tint = template.colors.accentContent) }
            }
        }
    }
}

private fun LazyListScope.inviteItems(strings: ConversationCandidatePickerStrings, state: ConversationsUiState, contacts: List<ChatInviteContact>, show: Boolean, enabled: Boolean, onRequest: (() -> Unit)?, avatar: @Composable (ChatInviteContact, Modifier) -> Unit, onInvite: (ChatInviteContact) -> Unit) {
    if (!show || state.candidateHasMore) return
    item("invite-title") { PickerSectionHeader(strings.inviteTitle) }
    when {
        !enabled -> item("invite-permission") { QuataPermissionPromptCardContent(strings.invitePermission, strings.inviteAllow, onRequest != null, { onRequest?.invoke() }) }
        state.isInviteContactsLoading -> item("invite-loading") { Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        state.inviteContactsError != null -> item("invite-error") { Text(state.inviteContactsError.orEmpty(), color = MaterialTheme.colorScheme.error) }
        else -> items(contacts, key = { "invite:${it.id}" }) { contact -> InviteContactRow(contact, strings.inviteAction, avatar, { onInvite(contact) }) }
    }
}

@Composable private fun InviteContactRow(contact: ChatInviteContact, action: String, avatar: @Composable (ChatInviteContact, Modifier) -> Unit, onInvite: () -> Unit) { val t = quataTheme(); Surface(color = t.colors.surface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().border(1.dp, t.colors.divider, RoundedCornerShape(18.dp))) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { avatar(contact, Modifier.size(48.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(contact.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(contact.phone, color = t.colors.textSecondary, fontSize = 13.sp, maxLines = 1) }; Button(onInvite, colors = ButtonDefaults.buttonColors(containerColor = t.colors.accent, contentColor = t.colors.accentContent), shape = RoundedCornerShape(14.dp)) { Text(action, fontWeight = FontWeight.Bold) } } } }
@Composable private fun PickerSectionHeader(title: String) { val t = quataTheme(); Text(title, color = t.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
@Composable private fun PickerNeighborhoodHeader(title: String) { val t = quataTheme(); Text(title, color = t.colors.textSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp)) }

internal fun filterPickerInviteContacts(contacts: List<ChatInviteContact>, query: String): List<ChatInviteContact> { val clean = query.trim(); if (clean.isBlank()) return contacts; val digits = clean.filter(Char::isDigit); return contacts.filter { it.displayName.contains(clean, true) || it.phone.contains(clean, true) || (digits.isNotEmpty() && it.phoneKeys.any { key -> key.contains(digits) }) } }
