package com.quata.core.captions.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CaptionLayoutCalculatorTest {
    @Test
    fun wrapsWordsUsingPlatformNeutralMetrics() {
        val segment = CaptionSegment(
            words = listOf(WordTiming("uno", 0, 100), WordTiming("dos", 100, 200), WordTiming("tres", 200, 300)),
        )
        val layout = CaptionLayoutCalculator().layout(
            segment,
            CaptionLayoutTemplate(maxWidthRatio = .5f, maxLines = 2, verticalPosition = .5f, lineHeightMultiplier = 1f, uppercase = false),
            object : CaptionTextMeasurer {
                override fun measureText(text: String) = text.length * 10f
                override val fontMetrics = CaptionFontMetrics(ascent = -8f, descent = 2f)
            },
            canvasWidth = 100,
            canvasHeight = 100,
        )

        assertEquals(listOf(0, 1, 1), layout.words.map { it.lineIndex })
        assertEquals(10f, layout.lineHeight)
    }
}
