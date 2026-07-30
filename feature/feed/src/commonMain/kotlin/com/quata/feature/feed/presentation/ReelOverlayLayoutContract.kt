package com.quata.feature.feed.presentation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Platform-independent reel overlay positions, measured from the reel edge.
 *
 * Android previously reached this location through a status-bar inset plus 14.dp of
 * content padding. Keeping the resolved value here prevents hosts with different
 * window-inset behavior from moving the chips.
 */
internal object ReelOverlayLayoutContract {
    val topChipsOffset: Dp = 68.dp
}
