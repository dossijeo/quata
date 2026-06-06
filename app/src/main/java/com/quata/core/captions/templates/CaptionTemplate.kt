package com.quata.core.captions.templates

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.StringRes
import com.quata.R

enum class CaptionTemplateStyle(@param:StringRes val labelRes: Int) {
    Karaoke(R.string.caption_template_karaoke),
    PopWord(R.string.caption_template_pop_word),
    Hormozi(R.string.caption_template_hormozi),
    Typewriter(R.string.caption_template_typewriter)
}

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

object CaptionTemplates {
    fun get(style: CaptionTemplateStyle): CaptionTemplate =
        when (style) {
            CaptionTemplateStyle.Karaoke -> CaptionTemplate(
                style = style,
                textSizeRatio = 0.058f,
                maxWidthRatio = 0.84f,
                maxLines = 2,
                verticalPosition = 0.74f,
                lineHeightMultiplier = 1.12f,
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD),
                uppercase = true,
                textColor = Color.WHITE,
                activeTextColor = Color.rgb(255, 122, 24),
                strokeColor = Color.BLACK,
                strokeWidthRatio = 0f,
                segmentBackgroundColor = Color.argb(176, 0, 0, 0),
                shadowRadiusRatio = 0f
            )
            CaptionTemplateStyle.PopWord -> CaptionTemplate(
                style = style,
                textSizeRatio = 0.092f,
                maxWidthRatio = 0.92f,
                maxLines = 1,
                verticalPosition = 0.67f,
                lineHeightMultiplier = 1f,
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD),
                uppercase = true,
                textColor = Color.WHITE,
                activeTextColor = Color.BLACK,
                strokeColor = Color.BLACK,
                strokeWidthRatio = 0f,
                activeBackgroundColor = Color.rgb(255, 138, 26),
                cornerRadiusRatio = 0.026f,
                shadowRadiusRatio = 0f
            )
            CaptionTemplateStyle.Hormozi -> CaptionTemplate(
                style = style,
                textSizeRatio = 0.066f,
                maxWidthRatio = 0.88f,
                maxLines = 2,
                verticalPosition = 0.70f,
                lineHeightMultiplier = 1.14f,
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD),
                uppercase = true,
                textColor = Color.WHITE,
                activeTextColor = Color.BLACK,
                strokeColor = Color.BLACK,
                strokeWidthRatio = 0f,
                activeBackgroundColor = Color.rgb(255, 229, 0),
                segmentBackgroundColor = Color.argb(210, 0, 0, 0),
                shadowRadiusRatio = 0f
            )
            CaptionTemplateStyle.Typewriter -> CaptionTemplate(
                style = style,
                textSizeRatio = 0.06f,
                maxWidthRatio = 0.82f,
                maxLines = 2,
                verticalPosition = 0.76f,
                lineHeightMultiplier = 1.16f,
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
                uppercase = false,
                textColor = Color.WHITE,
                activeTextColor = Color.WHITE,
                strokeColor = Color.rgb(18, 20, 30),
                strokeWidthRatio = 0f,
                segmentBackgroundColor = Color.argb(205, 38, 41, 50),
                shadowRadiusRatio = 0f
            )
        }
}
