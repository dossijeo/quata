package com.quata.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        if (expanded) {
            Surface(
                modifier = Modifier.width(196.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                Column {
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
        FeedIconAction(
            icon = Icons.Filled.MoreVert,
            description = liveLabel,
            onClick = { onExpandedChange(!expanded) },
        )
    }
}
