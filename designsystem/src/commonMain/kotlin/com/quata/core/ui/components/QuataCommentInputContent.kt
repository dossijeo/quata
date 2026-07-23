package com.quata.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text(strings.placeholder) },
            leadingIcon = leadingAction,
            trailingIcon = {
                CompactIconButton(enabled = draft.text.isNotBlank(), onClick = {
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
            modifier = Modifier.weight(1f).requiredHeightIn(min = 58.dp).onFocusChanged { if (it.isFocused) onFocused() },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
    }
}
