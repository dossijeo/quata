package com.quata.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.quata.core.model.PostComment

data class QuataCommentInputStrings(val placeholder: String, val send: String)

@Composable
fun QuataCommentInputContent(
    postId: String,
    draft: TextFieldValue,
    replyTarget: PostComment?,
    canParticipate: Boolean,
    currentUserLabel: String,
    strings: QuataCommentInputStrings,
    timestamp: () -> String,
    leadingAction: @Composable () -> Unit,
    onDraftChange: (TextFieldValue) -> Unit,
    onAuthRequired: () -> Unit,
    onAddComment: (PostComment) -> Unit,
    onCommentAdded: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    inputTestTag: String? = null,
    sendTestTag: String? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QuataEmojiCommentTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text(strings.placeholder) },
            leadingIcon = leadingAction,
            trailingIcon = {
                CompactIconButton(enabled = draft.text.isNotBlank(), testTag = sendTestTag, contentDescription = strings.send, onClick = {
                    if (canParticipate) {
                        val now = timestamp()
                        onAddComment(PostComment(
                            id = "local_${postId}_$now", authorName = currentUserLabel,
                            message = draft.text.trim(), timestamp = now,
                            replyToAuthorName = replyTarget?.authorName, replyToMessage = replyTarget?.message,
                            replyToCommentId = replyTarget?.id
                        ))
                        onCommentAdded()
                    } else onAuthRequired()
                }) { CompactIcon(Icons.AutoMirrored.Filled.Send, strings.send) }
            },
            onFocused = onFocused,
            modifier = Modifier
                .weight(1f)
                .requiredHeightIn(min = 58.dp)
                .then(inputTestTag?.let { Modifier.testTag(it) } ?: Modifier)
        )
    }
}
