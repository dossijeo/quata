package com.quata.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared landscape structure for the comments panel.
 *
 * Hosts provide rows, input, emoji handling and platform-specific actions as slots. This keeps
 * navigation, IME, translation overlays and pointer dismissal outside of common UI.
 */
@Composable
fun QuataCommentsPanelLandscapeContent(
    header: @Composable (Modifier) -> Unit,
    closeAction: @Composable () -> Unit,
    comments: @Composable (Modifier) -> Unit,
    input: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    replyTarget: (@Composable () -> Unit)? = null,
    emojiPanel: (@Composable BoxScope.() -> Unit)? = null,
    errorMessage: String? = null,
    errorTestTag: String = QuataCommentsPanelErrorTestTag,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                header(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                closeAction()
            }
            Spacer(Modifier.height(12.dp))
            comments(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 140.dp),
            )
            replyTarget?.let {
                Spacer(Modifier.height(10.dp))
                it()
            }
            errorMessage?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(10.dp))
                QuataCommentsPanelErrorContent(it, testTag = errorTestTag)
            }
            Spacer(Modifier.height(12.dp))
            input(Modifier.requiredHeightIn(min = 64.dp))
        }
        emojiPanel?.invoke(this)
    }
}
