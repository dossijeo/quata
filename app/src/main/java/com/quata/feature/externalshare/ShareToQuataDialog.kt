package com.quata.feature.externalshare

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.quata.R
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.platform.ClipboardService
import com.quata.core.text.localizedChatPreview
import com.quata.core.ui.components.AttachmentPreview
import com.quata.core.ui.components.AvatarImage
import com.quata.core.ui.components.QuataAvatarFallback
import com.quata.core.ui.components.QuataStandardFloatingPanel
import com.quata.core.ui.components.openAttachmentWithChooser
import com.quata.core.navigation.AppDestinations
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.chat.presentation.chatDisplayTitle
import com.quata.feature.chat.presentation.conversations.ConversationCandidatePickerStrings

@Composable
fun ShareToQuataDialog(
    payload: ExternalSharePayload,
    repository: ChatRepository,
    clipboardService: ClipboardService,
    onDismiss: () -> Unit,
    onSent: (String?) -> Unit
) {
    val context = LocalContext.current
    ExternalShareDestinationHostContent(
        payload = payload,
        repository = repository,
        clipboardService = clipboardService,
        strings = ExternalShareDestinationStrings(
            title = stringResource(R.string.share_with_quata),
            sending = stringResource(R.string.share_to_quata_sending),
            close = stringResource(R.string.common_close),
            payloadTextLabel = stringResource(R.string.share_to_quata_shared_text),
            attachmentsLabel = { count -> pluralStringResource(R.plurals.share_to_quata_attachments, count, count) },
            picker = ConversationCandidatePickerStrings(
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
            sendContentDescription = stringResource(R.string.common_send),
        ),
        onDismiss = onDismiss,
        onSent = onSent,
        panelHost = { content ->
            QuataStandardFloatingPanel(onDismiss = onDismiss, template = quataTheme()) { modifier, landscape ->
                content(modifier, landscape)
            }
        },
        candidateAvatar = { candidate, modifier ->
            AvatarImage(candidate.displayName, candidate.avatarUrl, profileId = candidate.profileId, modifier = modifier)
        },
        inviteAvatar = { contact, modifier ->
            QuataAvatarFallback(name = contact.displayName, stableId = contact.id, modifier = modifier)
        },
        attachmentContent = { attachment, modifier, onOpen ->
            TextButton(onClick = onOpen, modifier = modifier) { Text(attachment.name) }
        },
        onOpenAttachment = { attachment ->
            context.openAttachmentWithChooser(AttachmentPreview(attachment.name, attachment.uri, attachment.mimeType))
        },
        viewModelFactory = { sharePayload, chatRepository ->
            ShareToQuataViewModel(
                repository = chatRepository,
                payload = sharePayload,
                text = context.applicationContext::shareText,
                resolvePreview = context.applicationContext::localizedChatPreview,
                conversationTitle = { it.chatDisplayTitle() },
                isFavoriteConversation = { it == AppDestinations.FavoriteMessagesConversationId },
            )
        },
    )
}

private fun android.content.Context.shareText(value: ShareText): String = getString(
    when (value) {
        ShareText.SendError -> R.string.share_to_quata_send_error
        ShareText.LoadCandidates -> R.string.chat_error_load_candidates
    }
)
