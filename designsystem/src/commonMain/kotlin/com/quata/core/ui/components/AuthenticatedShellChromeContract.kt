package com.quata.core.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pure layout contract used by every authenticated Compose host. */
internal object AuthenticatedShellChromeContract {
    val topChromeHeight: Dp = 68.dp
    val offlineBannerHeight: Dp = 28.dp
    val bottomNavigationHeight: Dp = 92.dp
    val headerHorizontalInset: Dp = 16.dp
    val headerContentTopInset: Dp = 14.dp
    val logoSize: Dp = 32.dp
    val notificationsOffset: Dp = 54.dp
    val notificationsSize: Dp = 36.dp
    val sosWidth: Dp = 70.dp
    val sosHeight: Dp = 34.dp

    fun contentTop(safeTop: Dp, offline: Boolean): Dp =
        safeTop + topChromeHeight + if (offline) offlineBannerHeight else 0.dp

    fun contentBottomInset(safeBottom: Dp): Dp = bottomNavigationHeight + safeBottom
}
