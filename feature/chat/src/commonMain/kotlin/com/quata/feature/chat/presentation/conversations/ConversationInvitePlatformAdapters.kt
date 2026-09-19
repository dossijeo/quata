package com.quata.feature.chat.presentation.conversations

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.quata.core.platform.ClipboardService
import com.quata.core.platform.PlatformContact
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.SharePayload
import com.quata.core.platform.ShareService
import com.quata.core.ui.components.QuataStandardFloatingPanelContent
import com.quata.feature.chat.domain.ChatInviteContact
import com.quata.feature.chat.domain.normalizeContactPhoneKey
import kotlinx.coroutines.launch

/** Maps contacts explicitly selected by a platform picker into the common discovery contract. */
fun platformContactsForChatInvites(contacts: List<PlatformContact>): List<ChatInviteContact> = contacts
    .mapNotNull { contact ->
        val phones = contact.phones.map(String::trim).filter(String::isNotBlank)
        val phoneKeys = phones.map(::normalizeContactPhoneKey).filter { it.length in 6..20 }.toSet()
        val phone = phones.firstOrNull { normalizeContactPhoneKey(it) in phoneKeys } ?: return@mapNotNull null
        val internationalPhone = phones.firstOrNull { it.startsWith("+") }
        ChatInviteContact(
            id = "platform-contact:${phoneKeys.sorted().joinToString(":")}",
            displayName = contact.displayName?.trim().orEmpty().ifBlank { phone },
            phone = phone,
            phoneKeys = phoneKeys,
            internationalPhone = internationalPhone,
        )
    }
    .distinctBy(ChatInviteContact::id)

/** One real OS/browser share destination plus the common copy affordance. */
@Composable
fun PlatformInviteChannelSheet(
    contact: ChatInviteContact,
    strings: ConversationInvitationStrings,
    clipboardService: ClipboardService,
    shareService: ShareService,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    InviteChannelSheetContent(
        invitationMessage = strings.message,
        targets = listOf(InviteChannelTargetUi(id = "platform-share", label = strings.shareTarget)),
        strings = InviteChannelSheetStrings(
            shareTextTitle = strings.sheetTitle(contact.displayName),
            copyMessage = strings.copyMessage,
            chooseAppFor = strings.chooseAppFor(contact.displayName),
        ),
        clipboardService = clipboardService,
        onDismiss = onDismiss,
        onTargetSelected = {
            scope.launch {
                when (shareService.share(SharePayload(text = strings.message, title = strings.shareTitle))) {
                    is PlatformResult.Success, PlatformResult.Cancelled -> onDismiss()
                    is PlatformResult.Failure, PlatformResult.Unsupported -> Unit
                }
            }
        },
        panelHost = { content ->
            QuataStandardFloatingPanelContent(onDismiss = onDismiss) { modifier, _ -> content(modifier) }
        },
    )
}
