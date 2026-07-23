package com.quata.designsystem.translation

import androidx.compose.ui.graphics.ImageBitmap

/** Common representation of a captured translator backdrop; capture itself is platform-specific. */
data class QuataTranslatorBackground(
    val image: ImageBitmap,
    val originLeftPx: Int,
    val originTopPx: Int,
    val widthPx: Int,
    val heightPx: Int,
    val navigationCropLeftPx: Int = 0,
    val navigationCropTopPx: Int = 0,
    val navigationCropRightPx: Int = 0,
    val navigationCropBottomPx: Int = 0,
) {
    val excludesNavigationBar: Boolean
        get() = navigationCropLeftPx > 0 || navigationCropTopPx > 0 ||
            navigationCropRightPx > 0 || navigationCropBottomPx > 0
}
