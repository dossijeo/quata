package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import com.quata.core.model.Post
import com.quata.core.model.PostComment
import com.quata.core.ui.components.CommunityEmojiLabels
import com.quata.core.ui.components.CommunityEmojiPanelContent
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.communityEmojiSections
import com.quata.core.ui.components.insertAtSelection

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
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(post.id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var isEmojiPickerVisible by rememberSaveable(post.id) { mutableStateOf(false) }
    CommunityProfileCommentsPanelContent(
        comments = post.comments + localComments,
        title = strings.title,
        closeContentDescription = strings.closeContentDescription,
        onDismiss = onDismiss,
        commentRow = { comment -> CommunityProfileCommentRowContent(comment) },
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
                    ) {
                        CompactIcon(Icons.Filled.InsertEmoticon, strings.showEmojis, tint = Color(0xFFFFC55C))
                    }
                },
                onValueChange = { draft = it },
                onFocused = { if (isEmojiPickerVisible) isEmojiPickerVisible = false },
                onSend = {
                    if (canParticipate) {
                        onAddComment(createComment(draft.text.trim()))
                        draft = TextFieldValue()
                        isEmojiPickerVisible = false
                    } else {
                        onAuthRequired()
                    }
                },
            )
        },
    )
}

data class CommunityProfileCommentsDialogStrings(
    val title: String,
    val closeContentDescription: String,
    val placeholder: String,
    val sendLabel: String,
    val showEmojis: String,
    val emojiLabels: CommunityEmojiLabels,
)
