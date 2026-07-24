package com.quata.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared portrait structure for the comments panel. Platform hosts retain translation, IME and
 * pointer-dismissal behavior, while supplying the rows, emoji picker and input as slots.
 */
@Composable
fun QuataCommentsPanelPortraitContent(
    header: @Composable () -> Unit,
    comments: @Composable (Modifier) -> Unit,
    input: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    emojiPanel: (@Composable () -> Unit)? = null,
    replyTarget: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(start = 20.dp, end = 20.dp, bottom = 48.dp),
    ) {
        header()
        Spacer(Modifier.height(16.dp))
        comments(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 180.dp),
        )
        Spacer(Modifier.height(18.dp))
        emojiPanel?.let {
            it()
            Spacer(Modifier.height(18.dp))
        }
        replyTarget?.let {
            it()
            Spacer(Modifier.height(14.dp))
        }
        input(Modifier.requiredHeightIn(min = 82.dp))
    }
}
