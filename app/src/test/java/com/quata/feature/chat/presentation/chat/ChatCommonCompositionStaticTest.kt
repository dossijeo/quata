package com.quata.feature.chat.presentation.chat

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevents Web/iOS Chat from regressing to the former title/messages/text-field subset. */
class ChatCommonCompositionStaticTest {
    private val root = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun commonConversationOwnsTheCompleteInteractiveHierarchy() {
        val source = File(
            root,
            "feature/chat/src/commonMain/kotlin/com/quata/feature/chat/presentation/chat/ChatScreenHost.kt",
        ).readText()
        listOf(
            "FavoriteMessagesHeaderContent(",
            "ChatConversationTitleBarContent(",
            "ChatSelectedMessageActionBarContent(",
            "ChatConversationAvatarContent(",
            "ChatAttachmentQuickPanelContent(",
            "ChatComposerInputRowContent(",
            "CommunityEmojiPanelContent(",
            "ChatPendingAttachmentOverlayContent(",
            "ChatSosLocationContent(",
            "ChatDocumentAttachmentContent(",
            "FilePickerSource.Camera",
            "FilePickerSource.Gallery",
            "ChatTypingIndicatorContent(",
            "ChatMessageDeliveryIndicatorContent(",
            "retryPendingMessage(",
            "MemberInvitesChanged(",
            "PromoteModerator(",
            "RemoveParticipant(",
            "BlockParticipant(",
            "ChatPortableCandidatePanel(",
            "newMessagesLabel =",
            "historyHeader =",
            "service.writeText",
            "ChatUiEvent.HideConversation",
            "ChatUiEvent.DeleteConversation",
            "Ocultar conversación",
            "Eliminar conversación",
        ).forEach { required -> assertTrue("Missing shared Chat hierarchy token: $required", required in source) }
    }
}
