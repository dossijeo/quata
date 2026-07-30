package com.quata.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun QuataFeedOverflowActionAnchor(
    postRank: Int,
    rankLabel: String,
    liveLabel: String,
    reportLabel: String?,
    showReport: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenLive: () -> Unit,
    onReport: () -> Unit,
    modifier: Modifier = Modifier,
)
