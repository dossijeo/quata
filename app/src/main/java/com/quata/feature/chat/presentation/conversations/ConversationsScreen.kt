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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
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
import com.quata.core.model.User
import com.quata.core.platform.ClipboardService
import com.quata.core.text.localizedChatPreview
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.QuataAvatarFallback
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

/** Android adapter: permissions, resources, images and lifecycle are the only local concerns. */
@Composable
fun ConversationsScreen(
    padding: PaddingValues,
    repository: ChatRepository,
    clipboardService: ClipboardService,
    onOpenConversation: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null,
    onOpenFavorites: () -> Unit = {},
    viewModel: ConversationsAndroidViewModel = viewModel(factory = ConversationsAndroidViewModel.factory(repository, LocalContext.current)),
) {
    val context = LocalContext.current
    var contactsPermissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsPermissionGranted = granted
        if (granted) viewModel.loadInviteContacts()
    }
    ConversationsScreenHost(
        padding = padding,
        viewModel = viewModel.delegate,
        clipboardService = clipboardService,
        strings = ConversationsHostStrings(
            title = stringResource(R.string.conversations_title),
            searchPlaceholder = stringResource(R.string.conversations_search_placeholder),
            favoritesDescription = stringResource(R.string.conversation_favorites_title),
            newConversationDescription = stringResource(R.string.conversations_new_chat),
            undoDelete = stringResource(R.string.conversation_undo_delete),
            candidates = ConversationCandidatePickerStrings(
                searchPlaceholder = stringResource(R.string.conversations_new_chat_search_placeholder),
                noResults = stringResource(R.string.conversations_new_chat_no_results),
                cancel = stringResource(R.string.common_cancel), contacts = stringResource(R.string.conversations_new_chat_contacts),
                following = stringResource(R.string.conversations_new_chat_following), followers = stringResource(R.string.conversations_new_chat_followers),
                recent = stringResource(R.string.share_to_quata_recent_conversations), otherNeighborhoods = stringResource(R.string.conversations_new_chat_other_neighborhoods),
                unknownNeighborhood = stringResource(R.string.conversations_new_chat_unknown_neighborhood), inviteTitle = stringResource(R.string.conversations_invite_to_quata),
                invitePermission = stringResource(R.string.conversations_invite_contacts_permission), inviteAllow = stringResource(R.string.conversations_invite_allow),
                inviteAction = stringResource(R.string.conversations_invite_action), noneSelected = stringResource(R.string.conversation_forward_none_selected),
            ),
            conversationTitle = { it.chatDisplayTitle() },
            conversationPreview = context.applicationContext::localizedChatPreview,
            relativeUpdatedAt = { conversation, now -> conversation.relativeUpdatedAt(context, now) },
        ),
        onOpenConversation = onOpenConversation,
        onOpenUserProfile = onOpenUserProfile,
        openingProfileUserId = openingProfileUserId,
        onOpenFavorites = onOpenFavorites,
        contactsPermissionGranted = contactsPermissionGranted,
        onRequestInviteContactsPermission = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
        conversationAvatar = { conversation, state -> ConversationAvatar(conversation, state.currentUser, state.usersById, openingProfileUserId, onOpenUserProfile) },
        candidateAvatar = { candidate, modifier -> AvatarImage(name = candidate.displayName, avatarUrl = candidate.avatarUrl, profileId = candidate.profileId, modifier = modifier) },
        inviteAvatar = { contact, modifier -> QuataAvatarFallback(name = contact.displayName, stableId = contact.id, modifier = modifier) },
        panelHost = { content -> QuataStandardFloatingPanel(onDismiss = viewModel::closeNewConversationPicker, template = quataTheme()) { modifier, landscape -> content(modifier, landscape) } },
        inviteSheet = { contact, clipboard, dismiss -> InviteChannelSheet(contact, clipboard, dismiss) },
        nowMillisProvider = System::currentTimeMillis,
    )
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
    ConversationCandidatePickerDialogContent(
        state = state,
        clipboardService = clipboardService,
        strings = ConversationCandidatePickerStrings(
            searchPlaceholder = stringResource(R.string.conversations_new_chat_search_placeholder),
            noResults = stringResource(R.string.conversations_new_chat_no_results),
            cancel = stringResource(R.string.common_cancel),
            contacts = stringResource(R.string.conversations_new_chat_contacts),
            following = stringResource(R.string.conversations_new_chat_following),
            followers = stringResource(R.string.conversations_new_chat_followers),
            recent = stringResource(R.string.share_to_quata_recent_conversations),
            otherNeighborhoods = stringResource(R.string.conversations_new_chat_other_neighborhoods),
            unknownNeighborhood = stringResource(R.string.conversations_new_chat_unknown_neighborhood),
            inviteTitle = stringResource(R.string.conversations_invite_to_quata),
            invitePermission = stringResource(R.string.conversations_invite_contacts_permission),
            inviteAllow = stringResource(R.string.conversations_invite_allow),
            inviteAction = stringResource(R.string.conversations_invite_action),
            noneSelected = stringResource(R.string.conversation_forward_none_selected),
        ),
        onSearchChange = onSearchChange,
        onLoadMore = onLoadMore,
        onOpenCandidate = onOpenCandidate,
        onDismiss = onDismiss,
        panelHost = { content ->
            QuataStandardFloatingPanel(onDismiss = onDismiss, template = quataTheme()) { modifier, landscape -> content(modifier, landscape) }
        },
        candidateAvatar = { candidate, modifier ->
            AvatarImage(name = candidate.displayName, avatarUrl = candidate.avatarUrl, profileId = candidate.profileId, modifier = modifier)
        },
        inviteAvatar = { contact, modifier -> QuataAvatarFallback(name = contact.displayName, modifier = modifier, stableId = contact.id) },
        inviteSheet = if (onInviteContact != null) { { contact, clipboard, dismiss -> InviteChannelSheet(contact, clipboard, dismiss) } } else null,
        inviteContactsEnabled = inviteContactsEnabled,
        onRequestInviteContactsPermission = onRequestInviteContactsPermission,
        title = title,
        actionIcon = actionIcon,
        actionContentDescription = actionContentDescription,
        excludedProfileIds = excludedProfileIds,
        selectedCandidateIds = selectedCandidateIds,
        onToggleCandidate = onToggleCandidate,
        onConfirmSelection = onConfirmSelection,
        confirmEnabled = confirmEnabled,
        selectionSummary = selectionSummary,
        confirmIcon = confirmIcon,
        confirmContentDescription = confirmContentDescription,
    )
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
            QuataAvatarFallback(contact.displayName, modifier = Modifier.size(48.dp), stableId = contact.id)
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
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val targets = remember(contact) { availableInviteTargets(context, contact) }
    val message = stringResource(R.string.conversations_invite_message)
    val chooserTitle = stringResource(R.string.conversations_invite_chooser_title)
    val smsLabel = stringResource(R.string.conversations_invite_channel_sms)
    val template = quataTheme()
    val targetById = remember(targets) { targets.associateBy(InviteTarget::id) }
    InviteChannelSheetContent(
        invitationMessage = message,
        targets = targets.map { target ->
            InviteChannelTargetUi(
                id = target.id,
                label = if (target.route == InviteRoute.Sms) target.label.ifBlank { smsLabel } else target.label,
            )
        },
        strings = InviteChannelSheetStrings(
            shareTextTitle = stringResource(R.string.conversations_invite_text_to_share),
            copyMessage = stringResource(R.string.conversations_invite_copy_message),
            chooseAppFor = stringResource(R.string.conversations_invite_choose_app_for, contact.displayName),
        ),
        clipboardService = clipboardService,
        onDismiss = onDismiss,
        onTargetSelected = { uiTarget ->
            targetById[uiTarget.id]?.let { target ->
                onDismiss()
                launchQuataInvitation(context, contact, target, message, chooserTitle)
            }
        },
        panelHost = { content ->
            QuataFloatingPanel(
                onDismiss = onDismiss,
                template = template,
                portraitHeightFraction = 0.50f,
                landscapeWidthFraction = 0.74f,
                landscapeHeightFraction = 0.78f,
            ) { panelModifier, _ -> content(panelModifier) }
        },
        targetIcon = { uiTarget, modifier ->
            val target = targetById[uiTarget.id]
            if (target?.icon != null) {
                AsyncImage(
                    model = target.icon,
                    contentDescription = uiTarget.label,
                    contentScale = ContentScale.Fit,
                    modifier = modifier.padding(7.dp),
                )
            } else {
                Icon(
                    Icons.Default.ChatBubble,
                    contentDescription = uiTarget.label,
                    tint = template.colors.accent,
                    modifier = modifier.padding(14.dp),
                )
            }
        },
    )
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
                QuataAvatarFallback(item.chatDisplayTitle(), modifier = Modifier.size(46.dp))
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
