package com.quata.feature.chat.presentation.conversations

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.User
import com.quata.core.platform.ClipboardService
import com.quata.core.text.localizedChatPreview
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.AvatarLetter
import com.quata.core.ui.components.ClickableProfileAvatar
import com.quata.core.ui.components.QuataStandardFloatingPanel
import com.quata.core.ui.components.QuataFloatingPanel
import com.quata.core.ui.components.QuataCard
import com.quata.core.ui.components.QuataPermissionPromptCardContent
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.compactButtonMinSize
import com.quata.core.ui.window.rememberQuataWindowLayoutInfo
import com.quata.feature.chat.domain.ChatConversationCandidate
import com.quata.feature.chat.domain.ChatInviteContact
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chatDisplayTitle
import com.quata.feature.chat.presentation.relativeUpdatedAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConversationsScreen(
    padding: PaddingValues,
    repository: ChatRepository,
    clipboardService: ClipboardService,
    onOpenConversation: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null,
    onOpenFavorites: () -> Unit = {},
    viewModel: ConversationsAndroidViewModel = viewModel(factory = ConversationsAndroidViewModel.factory(repository, LocalContext.current))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var contactsPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var contactsPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsPermissionGranted = granted
        contactsPermissionRequested = true
        if (granted) viewModel.loadInviteContacts()
    }
    var timestampNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val visibleConversations = remember(context, state.conversations, state.messagesByConversation, state.usersById, query) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            state.conversations
        } else {
            state.conversations.filter { conversation ->
                val messages = state.messagesByConversation[conversation.id].orEmpty()
                val rawPreview = messages.lastOrNull()?.text ?: conversation.lastMessagePreview
                val preview = context.localizedChatPreview(rawPreview)
                val participantNames = conversation.participantIds
                    .mapNotNull { state.usersById[it]?.displayName }
                    .joinToString(" ")
                listOf(
                    conversation.chatDisplayTitle(),
                    conversation.title,
                    conversation.participantNames.joinToString(" "),
                    participantNames,
                    preview
                ).any { value -> value.contains(cleanQuery, ignoreCase = true) }
            }
        }
    }

    val isLandscapeLayout = rememberQuataWindowLayoutInfo().isLandscape
    val contentPadding = if (isLandscapeLayout) {
        PaddingValues(start = 8.dp, top = 18.dp, end = 18.dp, bottom = 18.dp)
    } else {
        PaddingValues(18.dp)
    }

    QuataScreen(padding) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                ConversationsListHeaderContent(
                    title = stringResource(R.string.conversations_title),
                    query = query,
                    searchPlaceholder = stringResource(R.string.conversations_search_placeholder),
                    onQueryChange = { query = it },
                    trailingAction = {
                        CompactIconButton(onClick = onOpenFavorites) {
                            CompactIcon(Icons.Filled.Star, contentDescription = stringResource(R.string.conversation_favorites_title), tint = QuataOrange)
                        }
                    }
                )
                Spacer(Modifier.padding(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.isLoading && state.conversations.isEmpty()) {
                        items(6) { index ->
                            ConversationListLoadingSkeletonContent(pulseDelayMillis = index * 85)
                        }
                    } else {
                        items(visibleConversations) { item ->
                            ConversationCard(
                                item = item,
                                messages = state.messagesByConversation[item.id].orEmpty(),
                                currentUser = state.currentUser,
                                usersById = state.usersById,
                                openingProfileUserId = openingProfileUserId,
                                timestampNowMillis = timestampNowMillis,
                                onOpenUserProfile = onOpenUserProfile,
                                onOpenConversation = onOpenConversation
                            )
                        }
                    }
                }
            }
            state.pendingDeletedConversation?.let { conversation ->
                UndoDeleteButton(
                    title = conversation.chatDisplayTitle(),
                    onUndo = { viewModel.onEvent(ConversationsUiEvent.RestoreDeletedConversation) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.72f)
                        .padding(18.dp)
                )
            }
            NewConversationFab(
                onClick = { viewModel.openNewConversationPicker() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
            )
        }
    }

    if (state.isNewConversationPickerOpen) {
        ConversationCandidatePickerDialog(
            state = state,
            clipboardService = clipboardService,
            onSearchChange = viewModel::onCandidateQueryChanged,
            onLoadMore = viewModel::loadMoreConversationCandidates,
            onOpenCandidate = { candidate ->
                viewModel.openCandidateConversation(candidate, onOpenConversation)
            },
            inviteContactsEnabled = contactsPermissionGranted,
            onRequestInviteContactsPermission = {
                contactsPermissionRequested = true
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onInviteContact = {},
            onDismiss = viewModel::closeNewConversationPicker
        )
    }

    LaunchedEffect(state.isNewConversationPickerOpen) {
        if (!state.isNewConversationPickerOpen) return@LaunchedEffect
        contactsPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (contactsPermissionGranted) {
            viewModel.loadInviteContacts()
        } else if (!contactsPermissionRequested) {
            contactsPermissionRequested = true
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    LaunchedEffect(state.pendingDeletedConversation?.id) {
        if (state.pendingDeletedConversation == null) return@LaunchedEffect
        delay(4_000L)
        viewModel.onEvent(ConversationsUiEvent.FinalizeDeletedConversation)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            timestampNowMillis = System.currentTimeMillis()
        }
    }
}

@Composable
private fun UndoDeleteButton(
    title: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    ConversationDeleteUndoContent(
        title = title,
        undoLabel = stringResource(R.string.conversation_undo_delete),
        onUndo = onUndo,
        modifier = modifier,
    )
}

@Composable
private fun NewConversationFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = template.colors.accent,
        shape = CircleShape,
        shadowElevation = 8.dp,
        modifier = modifier
            .size(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CompactIcon(
                imageVector = Icons.Filled.ChatBubble,
                contentDescription = stringResource(R.string.conversations_new_chat),
                tint = template.colors.accentContent,
                modifier = Modifier.size(34.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(template.colors.surfaceRaised)
                    .border(1.dp, template.colors.accent.copy(alpha = 0.65f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CompactIcon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = template.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationCandidatePickerDialog(
    state: ConversationsUiState,
    clipboardService: ClipboardService,
    onSearchChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenCandidate: (ChatConversationCandidate) -> Unit,
    onDismiss: () -> Unit,
    inviteContactsEnabled: Boolean = false,
    onRequestInviteContactsPermission: (() -> Unit)? = null,
    onInviteContact: ((ChatInviteContact) -> Unit)? = null,
    title: String = stringResource(R.string.conversations_new_chat_title),
    actionIcon: ImageVector = Icons.Filled.ChatBubble,
    actionContentDescription: String = stringResource(R.string.common_chat),
    excludedProfileIds: Set<String> = emptySet(),
    selectedCandidateIds: Set<String> = emptySet(),
    onToggleCandidate: ((ChatConversationCandidate) -> Unit)? = null,
    onConfirmSelection: (() -> Unit)? = null,
    confirmEnabled: Boolean = selectedCandidateIds.isNotEmpty(),
    selectionSummary: String = "",
    confirmIcon: ImageVector = Icons.AutoMirrored.Filled.Send,
    confirmContentDescription: String = stringResource(R.string.common_send)
) {
    val template = quataTheme()
    val listState = rememberLazyListState()
    val contactsTitle = stringResource(R.string.conversations_new_chat_contacts)
    val followingTitle = stringResource(R.string.conversations_new_chat_following)
    val followersTitle = stringResource(R.string.conversations_new_chat_followers)
    val recentTitle = stringResource(R.string.share_to_quata_recent_conversations)
    val otherTitle = stringResource(R.string.conversations_new_chat_other_neighborhoods)
    val unknownNeighborhood = stringResource(R.string.conversations_new_chat_unknown_neighborhood)
    val displayItems = remember(
        state.conversationCandidates,
        state.candidateActorNeighborhood,
        excludedProfileIds,
        contactsTitle,
        followingTitle,
        followersTitle,
        recentTitle,
        otherTitle,
        unknownNeighborhood
    ) {
        buildCandidateDisplayItems(
            candidates = state.conversationCandidates.filterNot { it.profileId in excludedProfileIds },
            actorNeighborhood = state.candidateActorNeighborhood,
            labels = CandidateDisplayLabels(
                contacts = contactsTitle,
                following = followingTitle,
                followers = followersTitle,
                recent = recentTitle,
                otherNeighborhoods = otherTitle,
                unknownNeighborhood = unknownNeighborhood
            )
        )
    }

    LaunchedEffect(listState, displayItems.size, state.candidateHasMore, state.isCandidatePageLoading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (displayItems.isNotEmpty() && lastVisible >= displayItems.lastIndex - 5) {
                    onLoadMore()
                }
            }
    }

    QuataStandardFloatingPanel(
        onDismiss = onDismiss,
        template = template
    ) { panelModifier, isLandscape ->
        NewConversationPanelContent(
                state = state,
                clipboardService = clipboardService,
                displayItems = displayItems,
                listState = listState,
                title = title,
                actionIcon = actionIcon,
                actionContentDescription = actionContentDescription,
                selectedCandidateIds = selectedCandidateIds,
                onToggleCandidate = onToggleCandidate,
                onConfirmSelection = onConfirmSelection,
                confirmEnabled = confirmEnabled,
                selectionSummary = selectionSummary,
                confirmIcon = confirmIcon,
                confirmContentDescription = confirmContentDescription,
                onSearchChange = onSearchChange,
                onOpenCandidate = onOpenCandidate,
                inviteContactsEnabled = inviteContactsEnabled,
                onRequestInviteContactsPermission = onRequestInviteContactsPermission,
                onInviteContact = onInviteContact,
                onDismiss = onDismiss,
                modifier = panelModifier.padding(
                    start = 20.dp,
                    top = if (isLandscape) 18.dp else 10.dp,
                    end = 20.dp,
                    bottom = if (isLandscape) 18.dp else 24.dp
                )
        )
    }
}

@Composable
private fun NewConversationPanelContent(
    state: ConversationsUiState,
    clipboardService: ClipboardService,
    displayItems: List<CandidateDisplayItem>,
    listState: LazyListState,
    title: String,
    actionIcon: ImageVector,
    actionContentDescription: String,
    selectedCandidateIds: Set<String>,
    onToggleCandidate: ((ChatConversationCandidate) -> Unit)?,
    onConfirmSelection: (() -> Unit)?,
    confirmEnabled: Boolean,
    selectionSummary: String,
    confirmIcon: ImageVector,
    confirmContentDescription: String,
    onSearchChange: (String) -> Unit,
    onOpenCandidate: (ChatConversationCandidate) -> Unit,
    inviteContactsEnabled: Boolean,
    onRequestInviteContactsPermission: (() -> Unit)?,
    onInviteContact: ((ChatInviteContact) -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    var pendingInviteContact by remember { mutableStateOf<ChatInviteContact?>(null) }
    val cleanQuery = state.candidateQuery.trim()
    val filteredInviteContacts = remember(state.inviteContacts, cleanQuery) {
        filterInviteContacts(state.inviteContacts, cleanQuery)
    }
    val canShowInviteSection = onInviteContact != null && !state.candidateHasMore
    val hasInviteContent = canShowInviteSection && (
        filteredInviteContacts.isNotEmpty() ||
            state.isInviteContactsLoading ||
            !inviteContactsEnabled ||
            state.inviteContactsError != null
        )
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            CompactIconButton(onClick = onDismiss) {
                CompactIcon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_cancel), tint = template.colors.textPrimary)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.candidateQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.conversations_new_chat_search_placeholder)) },
            leadingIcon = {
                CompactIcon(Icons.Filled.Search, contentDescription = null, tint = template.colors.textSecondary)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        state.candidateError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.isCandidateInitialLoading && state.conversationCandidates.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = template.colors.accent)
                }
            }
            displayItems.isEmpty() && !hasInviteContent -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.conversations_new_chat_no_results),
                        color = template.colors.textSecondary
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayItems, key = { it.key }) { item ->
                        when (item) {
                            is CandidateDisplayItem.SectionHeader -> CandidateSectionHeader(item.title)
                            is CandidateDisplayItem.NeighborhoodHeader -> CandidateNeighborhoodHeader(item.title)
                            is CandidateDisplayItem.CandidateRow -> CandidateUserCard(
                                candidate = item.candidate,
                                isOpening = state.openingCandidateProfileId == item.candidate.profileId,
                                actionIcon = actionIcon,
                                actionContentDescription = actionContentDescription,
                                isSelected = item.candidate.profileId in selectedCandidateIds,
                                onToggleSelection = onToggleCandidate?.let { toggle -> { toggle(item.candidate) } },
                                onOpen = { onOpenCandidate(item.candidate) }
                            )
                        }
                    }
                    if (state.isCandidatePageLoading) {
                        item(key = "loading-more") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = template.colors.accent, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    if (canShowInviteSection) {
                        item(key = "invite-section") {
                            CandidateSectionHeader(stringResource(R.string.conversations_invite_to_quata))
                        }
                        when {
                            !inviteContactsEnabled -> item(key = "invite-permission") {
                                InviteContactsPermissionCard(onRequestInviteContactsPermission)
                            }
                            state.isInviteContactsLoading -> item(key = "invite-loading") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = template.colors.accent, modifier = Modifier.size(24.dp))
                                }
                            }
                            state.inviteContactsError != null -> item(key = "invite-error") {
                                Text(state.inviteContactsError.orEmpty(), color = MaterialTheme.colorScheme.error)
                            }
                            else -> items(filteredInviteContacts, key = { "invite:${it.id}" }) { contact ->
                                InviteContactCard(contact = contact, onInvite = { pendingInviteContact = contact })
                            }
                        }
                    }
                }
            }
        }
        onConfirmSelection?.let { confirm ->
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
                    .background(template.colors.surface.copy(alpha = 0.76f), RoundedCornerShape(18.dp))
                    .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectionSummary.ifBlank { stringResource(R.string.conversation_forward_none_selected) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = template.colors.textPrimary.copy(alpha = if (selectedCandidateIds.isEmpty()) 0.54f else 0.94f),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = confirm,
                    enabled = confirmEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(46.dp)
                        .compactButtonMinSize(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    CompactIcon(confirmIcon, contentDescription = confirmContentDescription, tint = template.colors.accentContent)
                }
            }
        }
    }
    pendingInviteContact?.let { contact ->
        InviteChannelSheet(
            contact = contact,
            clipboardService = clipboardService,
            onDismiss = { pendingInviteContact = null }
        )
    }
}

@Composable
private fun InviteContactsPermissionCard(onRequestPermission: (() -> Unit)?) {
    QuataPermissionPromptCardContent(
        message = stringResource(R.string.conversations_invite_contacts_permission),
        actionLabel = stringResource(R.string.conversations_invite_allow),
        actionAvailable = onRequestPermission != null,
        onRequestPermission = { onRequestPermission?.invoke() },
    )
}

@Composable
private fun InviteContactCard(contact: ChatInviteContact, onInvite: () -> Unit) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarLetter(contact.displayName, modifier = Modifier.size(48.dp), stableId = contact.id)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(contact.phone, color = template.colors.textSecondary, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = onInvite,
                colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
                shape = RoundedCornerShape(14.dp),
                contentPadding = CompactButtonContentPadding
            ) {
                Text(stringResource(R.string.conversations_invite_action), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InviteChannelSheet(
    contact: ChatInviteContact,
    clipboardService: ClipboardService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val targets = remember(contact) { availableInviteTargets(context, contact) }
    val message = stringResource(R.string.conversations_invite_message)
    val chooserTitle = stringResource(R.string.conversations_invite_chooser_title)
    val smsLabel = stringResource(R.string.conversations_invite_channel_sms)
    val template = quataTheme()
    val scope = rememberCoroutineScope()
    QuataFloatingPanel(
        onDismiss = onDismiss,
        template = template,
        portraitHeightFraction = 0.50f,
        landscapeWidthFraction = 0.74f,
        landscapeHeightFraction = 0.78f
    ) { panelModifier, _ ->
        Column(
            modifier = panelModifier.padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                stringResource(R.string.conversations_invite_text_to_share),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = template.colors.textPrimary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Surface(
                color = template.colors.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        message,
                        color = template.colors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 14.dp, top = 11.dp, end = 58.dp, bottom = 11.dp)
                    )
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.conversations_invite_copy_message),
                        tint = template.colors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch {
                                    /* Superseded by the platform-neutral service below.
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("Qüata", message))
                                    )
                                    */
                                    clipboardService.writeText(message)
                                }
                            }
                            .padding(12.dp)
                            .size(24.dp)
                    )
                }
            }
            HorizontalDivider(
                color = template.colors.divider,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            Text(
                stringResource(R.string.conversations_invite_choose_app_for, contact.displayName),
                fontSize = 14.sp,
                color = template.colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
            ) {
                items(targets, key = { it.id }) { target ->
                    InviteTargetItem(
                        target = target,
                        label = when (target.route) {
                            InviteRoute.Sms -> target.label.ifBlank { smsLabel }
                            else -> target.label
                        },
                        onClick = {
                            onDismiss()
                            launchQuataInvitation(context, contact, target, message, chooserTitle)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InviteTargetItem(target: InviteTarget, label: String, onClick: () -> Unit) {
    val template = quataTheme()
    Column(
        modifier = Modifier
            .width(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = template.colors.surface,
            modifier = Modifier.size(58.dp)
        ) {
            when {
                target.icon != null -> AsyncImage(
                    model = target.icon,
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(7.dp)
                )
                else -> Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = label,
                    tint = template.colors.accent,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = template.colors.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

internal fun filterInviteContacts(contacts: List<ChatInviteContact>, query: String): List<ChatInviteContact> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank()) return contacts
    val queryDigits = cleanQuery.filter(Char::isDigit)
    return contacts.filter { contact ->
        contact.displayName.contains(cleanQuery, ignoreCase = true) ||
            contact.phone.contains(cleanQuery, ignoreCase = true) ||
            (queryDigits.isNotEmpty() && contact.phoneKeys.any { key -> key.contains(queryDigits) })
    }
}

@Composable
private fun CandidateSectionHeader(title: String) {
    val template = quataTheme()
    Text(
        text = title,
        color = template.colors.textPrimary,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun CandidateNeighborhoodHeader(title: String) {
    val template = quataTheme()
    Text(
        text = title,
        color = template.colors.textSecondary,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 6.dp, top = 4.dp)
    )
}

@Composable
private fun CandidateUserCard(
    candidate: ChatConversationCandidate,
    isOpening: Boolean,
    actionIcon: ImageVector,
    actionContentDescription: String,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    onOpen: () -> Unit
) {
    ConversationCandidateCardContent(
        title = candidate.displayName,
        subtitle = candidate.neighborhood,
        isOpening = isOpening,
        actionIcon = actionIcon,
        actionContentDescription = actionContentDescription,
        isSelected = isSelected,
        onToggleSelection = onToggleSelection,
        onOpen = onOpen,
        avatar = {
            AvatarImage(
                name = candidate.displayName,
                avatarUrl = candidate.avatarUrl,
                profileId = candidate.profileId,
                modifier = Modifier.size(48.dp)
            )
        }
    )
}

@Composable
private fun ConversationCard(
    item: Conversation,
    messages: List<Message>,
    currentUser: User?,
    usersById: Map<String, User>,
    openingProfileUserId: String?,
    timestampNowMillis: Long,
    onOpenUserProfile: (String) -> Unit,
    onOpenConversation: (String) -> Unit
) {
    val context = LocalContext.current
    val rawPreview = messages.lastOrNull()?.text ?: item.lastMessagePreview
    val preview = context.localizedChatPreview(rawPreview)
    ConversationListItemContent(
        title = item.chatDisplayTitle(),
        preview = preview,
        updatedAt = item.relativeUpdatedAt(context, timestampNowMillis),
        unreadCount = item.unreadCount,
        showUnreadBadge = !item.isMuted,
        avatar = {
            ConversationAvatar(item, currentUser, usersById, openingProfileUserId, onOpenUserProfile)
        },
        onOpen = { onOpenConversation(item.id) }
    )
}

@Composable
private fun ConversationAvatar(
    item: Conversation,
    currentUser: User?,
    usersById: Map<String, User>,
    openingProfileUserId: String?,
    onOpenUserProfile: (String) -> Unit
) {
    val template = quataTheme()
    val privateUser = item.participantIds
        .firstOrNull { it != currentUser?.id }
        ?.let { usersById[it] }
    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        if (item.isEmergency) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(template.colors.sosSurface)
                    .border(1.dp, template.colors.accent.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.common_sos), color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = template.textSizes.caption)
            }
        } else if (item.isGroup) {
            AvatarImage(
                name = item.chatDisplayTitle(),
                avatarUrl = item.avatarUrl,
                profileId = item.id,
                modifier = Modifier.size(46.dp)
            )
        } else {
            if (privateUser != null) {
                ClickableProfileAvatar(
                    name = privateUser.displayName,
                    avatarUrl = privateUser.avatarUrl,
                    profileId = privateUser.id,
                    isLoading = openingProfileUserId == privateUser.id,
                    onClick = { onOpenUserProfile(privateUser.id) },
                    modifier = Modifier.size(46.dp)
                )
            } else {
                AvatarLetter(item.chatDisplayTitle(), modifier = Modifier.size(46.dp))
            }
        }
        if (item.isMuted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(template.colors.surfaceRaised)
                    .border(1.dp, template.colors.divider, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83D\uDD15", fontSize = 13.sp)
            }
        }
    }
}
