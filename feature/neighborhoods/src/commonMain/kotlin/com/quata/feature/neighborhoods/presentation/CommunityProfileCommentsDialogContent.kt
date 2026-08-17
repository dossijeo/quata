package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.CommunityEmojiPanelContent
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataReplyTargetBannerContent
import com.quata.core.ui.components.communityEmojiSections
import com.quata.core.ui.components.insertAtSelection
import com.quata.designsystem.translation.LocalQuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatableTextRegistry
import com.quata.designsystem.translation.QuataTranslatorGateway
import com.quata.designsystem.translation.QuataTranslatorOverlayContent
import com.quata.designsystem.translation.QuataTranslatorStrings
import com.quata.designsystem.translation.quataTranslatableText

/**
 * Shared profile-gallery comments overlay.
 *
 * The component owns only portable draft state and composition of the common comments panel.
 * Hosts retain authorization, persistence and local-comment identity through [createComment] and
 * [onAddComment], so no platform clock, resource or navigation API leaks into commonMain.
 */
@Composable
fun CommunityProfileCommentsDialogContent(
    post: Post,
    localComments: List<PostComment>,
    canParticipate: Boolean,
    strings: CommunityProfileCommentsDialogStrings,
    onAuthRequired: () -> Unit,
    createComment: (draft: String) -> PostComment,
    onAddComment: (PostComment) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    translatorTrigger: @Composable (String, Modifier, () -> Unit, Boolean) -> Unit,
    translatorGateway: QuataTranslatorGateway?,
    translatorStrings: QuataTranslatorStrings,
) {
    var draft by rememberSaveable(post.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var replyTo by remember(post.id) { mutableStateOf<PostComment?>(null) }
    var isEmojiPickerVisible by rememberSaveable(post.id) { mutableStateOf(false) }
    val inheritedTranslatorRegistry = LocalQuataTranslatableTextRegistry.current
    val translatorRegistry = inheritedTranslatorRegistry ?: remember(post.id) { QuataTranslatableTextRegistry() }
    var translatorActive by rememberSaveable(post.id) { mutableStateOf(false) }
    val translatorEnabled = translatorGateway != null && translatorRegistry.visibleBoxes.isNotEmpty()
    fun openTranslator() {
        if (translatorEnabled) translatorActive = true
    }
    CompositionLocalProvider(LocalQuataTranslatableTextRegistry provides translatorRegistry) {
    CommunityProfileCommentsPanelContent(
        comments = post.comments + localComments,
        title = strings.title,
        closeContentDescription = strings.closeContentDescription,
        onDismiss = onDismiss,
        translatorAction = { modifier ->
            translatorTrigger(strings.translatorContentDescription, modifier.testTag("public-profile.comments.translator"), ::openTranslator, translatorEnabled)
        },
        commentRow = { comment ->
            val displayText = buildString {
                append(comment.authorName)
                comment.replyToAuthorName?.let { append('\n').append(strings.replyTo(it)) }
                comment.replyToMessage?.takeIf(String::isNotBlank)?.let { append('\n').append(it) }
                comment.message.takeIf(String::isNotBlank)?.let { append('\n').append(it) }
            }
            CommunityProfileCommentRowContent(
                comment = comment,
                modifier = Modifier.quataTranslatableText(
                    id = "public-profile-comment:${comment.id}",
                    text = comment.message,
                    displayText = displayText,
                ),
                onOpenAuthorProfile = onOpenUserProfile,
                replyLabel = strings.reply,
                replyToLabel = strings.replyTo,
                onReply = { replyTo = comment },
            )
        },
        replyTarget = replyTo?.let { target ->
            {
                QuataReplyTargetBannerContent(
                    comment = target,
                    replyingTo = strings.replyingTo(target.authorName),
                    cancelDescription = strings.cancelReply,
                    onClear = { replyTo = null },
                    targetTestTag = "public-profile.comments.replyTarget.${target.id}",
                    cancelTestTag = "public-profile.comments.replyCancel.${target.id}",
                )
            }
        },
        input = {
            if (isEmojiPickerVisible) {
                CommunityEmojiPanelContent(
                    communityEmojiSections(strings.emojiLabels),
                    onEmojiClick = { emoji -> draft = draft.insertAtSelection(emoji) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CommunityProfileCommentInputContent(
                value = draft,
                placeholder = strings.placeholder,
                sendLabel = strings.sendLabel,
                leadingAction = {
                    CompactIconButton(
                        onClick = { isEmojiPickerVisible = !isEmojiPickerVisible },
                        testTag = PublicProfileCommentsEmojiTestTag,
                        contentDescription = strings.showEmojis,
                    ) {
                        CompactIcon(Icons.Filled.InsertEmoticon, strings.showEmojis, tint = Color(0xFFFFC55C))
                    }
                },
                onValueChange = { draft = it },
                onFocused = { if (isEmojiPickerVisible) isEmojiPickerVisible = false },
                onSend = {
                    if (canParticipate) {
                        val target = replyTo
                        onAddComment(
                            createComment(draft.text.trim()).copy(
                                replyToAuthorName = target?.authorName,
                                replyToMessage = target?.message,
                                replyToCommentId = target?.id,
                            ),
                        )
                        draft = TextFieldValue()
                        replyTo = null
                        isEmojiPickerVisible = false
                    } else {
                        onAuthRequired()
                    }
                },
            )
        },
    )
    translatorGateway?.let { gateway ->
        if (translatorActive) {
            QuataTranslatorOverlayContent(
                registry = translatorRegistry,
                gateway = gateway,
                strings = translatorStrings,
                onDismiss = { translatorActive = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    }
}

data class CommunityProfileCommentsDialogStrings(
    val title: String,
    val closeContentDescription: String,
    val placeholder: String,
    val sendLabel: String,
    val showEmojis: String,
    val emojiLabels: CommunityEmojiLabels,
    val translatorContentDescription: String = "Traductor Fang",
    val reply: String = "Responder",
    val replyingTo: (String) -> String = { "Respondiendo a $it" },
    val cancelReply: String = "Cancelar respuesta",
    val replyTo: (String) -> String = { "En respuesta a $it" },
)
