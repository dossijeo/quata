package com.quata.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun QuataFeedOverflowActionAnchor(
    postRank: Int,
    rankLabel: String,
    liveLabel: String,
    reportLabel: String?,
    showReport: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenLive: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier) {
        FeedIconAction(Icons.Filled.MoreVert, liveLabel, onClick = { onExpandedChange(true) })
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text("$rankLabel #$postRank") },
                leadingIcon = { QuataFeedEmojiIcon(QuataFeedEmoji.Rank) },
                onClick = {
                    onExpandedChange(false)
                    onOpenLive()
                },
            )
            DropdownMenuItem(
                text = { Text(liveLabel) },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                onClick = {
                    onExpandedChange(false)
                    onOpenLive()
                },
            )
            if (showReport && reportLabel != null) {
                DropdownMenuItem(
                    text = { Text(reportLabel) },
                    leadingIcon = { Icon(Icons.Filled.Flag, null) },
                    onClick = {
                        onExpandedChange(false)
                        onReport()
                    },
                )
            }
        }
    }
}
