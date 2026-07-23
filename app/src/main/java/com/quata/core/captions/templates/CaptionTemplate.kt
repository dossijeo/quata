package com.quata.core.captions.templates

import android.graphics.Typeface

data class CaptionTemplate(
    val style: CaptionTemplateStyle,
    val textSizeRatio: Float,
    val maxWidthRatio: Float,
    val maxLines: Int,
    val verticalPosition: Float,
    val lineHeightMultiplier: Float,
    val typeface: Typeface,
    val uppercase: Boolean,
    val textColor: Int,
    val activeTextColor: Int,
    val strokeColor: Int,
    val strokeWidthRatio: Float,
    val activeBackgroundColor: Int? = null,
    val segmentBackgroundColor: Int? = null,
    val cornerRadiusRatio: Float = 0.018f,
    val shadowRadiusRatio: Float = 0.012f
) {
    fun displayText(text: String): String = if (uppercase) text.uppercase() else text
}

/** Android renderer adapter for the shared caption template specification. */
object CaptionTemplates {
    fun get(style: CaptionTemplateStyle): CaptionTemplate = CaptionTemplateSpecs.get(style).toAndroidTemplate(style)
}

private fun CaptionTemplateSpec.toAndroidTemplate(style: CaptionTemplateStyle) = CaptionTemplate(
    style = style,
    textSizeRatio = textSizeRatio,
    maxWidthRatio = maxWidthRatio,
    maxLines = maxLines,
    verticalPosition = verticalPosition,
    lineHeightMultiplier = lineHeightMultiplier,
    typeface = when (fontFamily) {
        CaptionFontFamily.SansBlack -> Typeface.create("sans-serif-black", Typeface.BOLD)
        CaptionFontFamily.SansCondensed -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
        CaptionFontFamily.Monospace -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    },
    uppercase = uppercase,
    textColor = textColorArgb.toInt(),
    activeTextColor = activeTextColorArgb.toInt(),
    strokeColor = strokeColorArgb.toInt(),
    strokeWidthRatio = strokeWidthRatio,
    activeBackgroundColor = activeBackgroundColorArgb?.toInt(),
    segmentBackgroundColor = segmentBackgroundColorArgb?.toInt(),
    cornerRadiusRatio = cornerRadiusRatio,
    shadowRadiusRatio = shadowRadiusRatio
)
