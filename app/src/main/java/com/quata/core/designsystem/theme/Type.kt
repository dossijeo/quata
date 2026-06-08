package com.quata.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

val QuataTypography = Typography()

data class QuataTextSizes(
    val badge: TextUnit,
    val tiny: TextUnit,
    val caption: TextUnit,
    val body: TextUnit,
    val bodyLarge: TextUnit,
    val title: TextUnit,
    val headline: TextUnit,
    val display: TextUnit
)

val QuataDefaultTextSizes = QuataTextSizes(
    badge = 9.sp,
    tiny = 11.sp,
    caption = 12.sp,
    body = 14.sp,
    bodyLarge = 16.sp,
    title = 18.sp,
    headline = 24.sp,
    display = 34.sp
)
