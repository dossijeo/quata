package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton
import com.quata.core.ui.components.QuataEmojiCommentTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send

const val PublicProfileCommentsInputTestTag = "public-profile.comments.input"
const val PublicProfileCommentsSendTestTag = "public-profile.comments.send"
const val PublicProfileCommentsEmojiTestTag = "public-profile.comments.emoji"

/** Shared comment composer row; hosts retain authorization and comment persistence in onSend. */
@Composable
fun CommunityProfileCommentInputContent(
    value: TextFieldValue,
    placeholder: String,
    sendLabel: String,
    leadingAction: @Composable () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onFocused: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        QuataEmojiCommentTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            leadingIcon = leadingAction,
            trailingIcon = {
                CompactIconButton(
                    enabled = value.text.isNotBlank(),
                    testTag = PublicProfileCommentsSendTestTag,
                    contentDescription = sendLabel,
                    onClick = onSend,
                ) {
                    CompactIcon(Icons.AutoMirrored.Filled.Send, sendLabel)
                }
            },
            onFocused = onFocused,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 58.dp)
                .testTag(PublicProfileCommentsInputTestTag),
        )
    }
}
