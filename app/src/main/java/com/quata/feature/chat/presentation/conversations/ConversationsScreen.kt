package com.quata.feature.chat.presentation.conversations

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.QuataAvatarLoadingHaloContent
import com.quata.R
import com.quata.core.platform.ClipboardService
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.domain.ChatInviteContact

/** Android owns lifecycle, resources, Coil and contact permission; the UI itself is common. */
@Composable
fun ConversationsScreen(
    padding: androidx.compose.foundation.layout.PaddingValues,
    repository: ChatRepository,
    clipboardService: ClipboardService,
    onOpenConversation: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    openingProfileUserId: String? = null,
    onOpenFavorites: () -> Unit = {},
    viewModel: ConversationsAndroidViewModel = viewModel(factory = ConversationsAndroidViewModel.factory(repository, LocalContext.current)),
) {
    val context = LocalContext.current
    var contactsGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    var contactsRequested by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsGranted = granted; contactsRequested = true
        if (granted) viewModel.loadInviteContacts()
    }
    val strings = androidConversationsStrings()
    val state by viewModel.uiState.collectAsState()
    ConversationsScreenHost(
        viewModel = viewModel.delegate,
        clipboardService = clipboardService,
        strings = strings,
        onOpenConversation = onOpenConversation,
        onOpenFavorites = onOpenFavorites,
        padding = padding,
        onOpenUserProfile = onOpenUserProfile,
        openingProfileUserId = openingProfileUserId,
        remoteAvatar = { name, url, _, modifier ->
            if (url.isNullOrBlank()) QuataAvatarFallback(name, modifier = modifier)
            else AsyncImage(model = url, contentDescription = name, modifier = modifier, contentScale = ContentScale.Crop)
        },
        avatarLoadingOverlay = { isLoading, modifier -> QuataAvatarLoadingHaloContent(isLoading, modifier) {} },
        inviteContactsEnabled = contactsGranted,
        onRequestInviteContactsPermission = { contactsRequested = true; launcher.launch(Manifest.permission.READ_CONTACTS) },
        inviteSheet = { contact, clipboard, dismiss -> AndroidInviteChannelSheet(contact, clipboard, dismiss) },
    )
    LaunchedEffect(state.isNewConversationPickerOpen) {
        if (!state.isNewConversationPickerOpen) return@LaunchedEffect
        if (contactsGranted) viewModel.loadInviteContacts()
        else if (!contactsRequested) { contactsRequested = true; launcher.launch(Manifest.permission.READ_CONTACTS) }
    }
}

@Composable
private fun AndroidInviteChannelSheet(contact: ChatInviteContact, clipboard: ClipboardService, dismiss: () -> Unit) {
    val context = LocalContext.current
    val targets = remember(contact) { availableInviteTargets(context, contact) }
    val byId = remember(targets) { targets.associateBy(InviteTarget::id) }
    val message = stringResource(R.string.conversations_invite_message)
    val chooserTitle = stringResource(R.string.conversations_invite_chooser_title)
    val smsLabel = stringResource(R.string.conversations_invite_channel_sms)
    val template = quataTheme()
    InviteChannelSheetContent(
        invitationMessage = message,
        targets = targets.map { InviteChannelTargetUi(it.id, if (it.route == InviteRoute.Sms) it.label.ifBlank { smsLabel } else it.label) },
        strings = InviteChannelSheetStrings(stringResource(R.string.conversations_invite_text_to_share), stringResource(R.string.conversations_invite_copy_message), stringResource(R.string.conversations_invite_choose_app_for, contact.displayName)),
        clipboardService = clipboard,
        onDismiss = dismiss,
        onTargetSelected = { target -> byId[target.id]?.let { dismiss(); launchQuataInvitation(context, contact, it, message, chooserTitle) } },
        panelHost = { content -> com.quata.core.ui.components.QuataFloatingPanel(onDismiss = dismiss, template = template, portraitHeightFraction = .50f, landscapeWidthFraction = .74f, landscapeHeightFraction = .78f) { modifier, _ -> content(modifier) } },
        targetIcon = { target, modifier ->
            byId[target.id]?.icon?.let { AsyncImage(it, target.label, modifier = modifier.padding(7.dp), contentScale = ContentScale.Fit) }
                ?: Icon(Icons.Default.ChatBubble, target.label, tint = template.colors.accent, modifier = modifier.padding(14.dp))
        },
    )
}

@Composable
private fun androidConversationsStrings(): ConversationsScreenStrings {
    val resources = LocalResources.current
    return ConversationsScreenStrings(
    title = stringResource(R.string.conversations_title), searchPlaceholder = stringResource(R.string.conversations_search_placeholder), favorites = stringResource(R.string.conversation_favorites_title), newChat = stringResource(R.string.conversations_new_chat), newChatTitle = stringResource(R.string.conversations_new_chat_title), empty = null, undo = stringResource(R.string.conversation_undo_delete), emergencyTitle = "🚨 ${stringResource(R.string.common_sos)}", sosLabel = stringResource(R.string.common_sos), sosLocationUpdate = stringResource(R.string.sos_location_update), sosLocationUnavailable = stringResource(R.string.sos_location_unavailable),
    photo = stringResource(R.string.conversation_preview_photo), video = stringResource(R.string.conversation_preview_video), document = stringResource(R.string.conversation_preview_document), voiceNote = stringResource(R.string.conversation_preview_voice_note), file = stringResource(R.string.conversation_preview_file),
    time = { resources.getString(R.string.time_seconds_ago, it) }, oneMinute = stringResource(R.string.time_one_minute_ago), minutes = { resources.getString(R.string.time_minutes_ago, it) }, hours = { resources.getString(R.string.time_hours_ago, it) }, days = { resources.getString(R.string.time_days_ago, it) }, oneWeek = stringResource(R.string.time_one_week_ago), weeks = { resources.getString(R.string.time_weeks_ago, it) }, oneMonth = stringResource(R.string.time_one_month_ago), months = { resources.getString(R.string.time_months_ago, it) }, oneYear = stringResource(R.string.time_one_year_ago), years = { resources.getString(R.string.time_years_ago, it) },
    loadCandidatesError = stringResource(R.string.chat_error_load_candidates),
    openConversationError = stringResource(R.string.chat_error_open_conversation),
    loadConversationsError = stringResource(R.string.chat_error_load_conversations),
    restoreConversationError = stringResource(R.string.chat_error_restore_conversation),
    deleteConversationError = stringResource(R.string.chat_error_delete_conversation),
    picker = ConversationCandidatePickerStrings(stringResource(R.string.conversations_new_chat_search_placeholder), stringResource(R.string.conversations_new_chat_no_results), stringResource(R.string.common_cancel), stringResource(R.string.conversations_new_chat_contacts), stringResource(R.string.conversations_new_chat_following), stringResource(R.string.conversations_new_chat_followers), stringResource(R.string.share_to_quata_recent_conversations), stringResource(R.string.conversations_new_chat_other_neighborhoods), stringResource(R.string.conversations_new_chat_unknown_neighborhood), stringResource(R.string.conversations_invite_to_quata), stringResource(R.string.conversations_invite_contacts_permission), stringResource(R.string.conversations_invite_allow), stringResource(R.string.conversations_invite_action), stringResource(R.string.conversation_forward_none_selected)),
    )
}
