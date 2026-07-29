package com.quata.feature.feed.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class FeedMediaPlaceholderContrastTest {
    @Test
    fun unavailableCopyKeepsAccessibleContrastOnTheMediaSurface() {
        val renderedStyle = feedMediaUnavailableTextStyle(TextStyle.Default)
        val contrast = contrastRatio(
            foreground = renderedStyle.color,
            background = FeedMediaBackgroundColor,
        )

        assertTrue(renderedStyle.color == FeedMediaUnavailableContentColor)
        assertTrue(
            contrast >= 4.5,
            "Media placeholder contrast must remain WCAG AA; actual ratio was $contrast",
        )
    }
}

private fun contrastRatio(foreground: Color, background: Color): Double {
    val light = max(relativeLuminance(foreground), relativeLuminance(background))
    val dark = min(relativeLuminance(foreground), relativeLuminance(background))
    return (light + 0.05) / (dark + 0.05)
}

private fun relativeLuminance(color: Color): Double =
    0.2126 * linearize(color.red.toDouble()) +
        0.7152 * linearize(color.green.toDouble()) +
        0.0722 * linearize(color.blue.toDouble())

private fun linearize(channel: Double): Double =
    if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
