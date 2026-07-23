package com.quata.core.captions.core

data class CaptionLayoutTemplate(
    val maxWidthRatio: Float,
    val maxLines: Int,
    val verticalPosition: Float,
    val lineHeightMultiplier: Float,
    val uppercase: Boolean,
) {
    fun displayText(text: String): String = if (uppercase) text.uppercase() else text
}

data class CaptionFontMetrics(val ascent: Float, val descent: Float)

interface CaptionTextMeasurer {
    fun measureText(text: String): Float
    val fontMetrics: CaptionFontMetrics
}

data class CaptionWordLayout(val word: WordTiming, val lineIndex: Int, val x: Float, val y: Float, val width: Float, val height: Float)
data class CaptionLayout(val words: List<CaptionWordLayout>, val lineHeight: Float)

/** Platform-neutral word wrapping and positioning; native renderers provide text metrics. */
class CaptionLayoutCalculator {
    fun layout(segment: CaptionSegment, template: CaptionLayoutTemplate, measurer: CaptionTextMeasurer, canvasWidth: Int, canvasHeight: Int): CaptionLayout {
        val maxWidth = canvasWidth * template.maxWidthRatio
        val space = measurer.measureText(" ").coerceAtLeast(canvasWidth * .012f)
        val metrics = measurer.fontMetrics
        val lineHeight = (metrics.descent - metrics.ascent) * template.lineHeightMultiplier
        val lines = mutableListOf<List<WordTiming>>()
        var current = mutableListOf<WordTiming>()
        var width = 0f
        segment.words.forEach { word ->
            val wordWidth = measurer.measureText(template.displayText(word.text))
            val nextWidth = if (current.isEmpty()) wordWidth else width + space + wordWidth
            if (current.isNotEmpty() && nextWidth > maxWidth && lines.size < template.maxLines - 1) {
                lines += current; current = mutableListOf(); width = 0f
            }
            current += word
            width = if (width == 0f) wordWidth else width + space + wordWidth
        }
        if (current.isNotEmpty()) lines += current
        val visible = lines.take(template.maxLines)
        val top = canvasHeight * template.verticalPosition - lineHeight * visible.size / 2f
        val layouts = mutableListOf<CaptionWordLayout>()
        visible.forEachIndexed { index, words ->
            val lineWidth = words.sumOf { measurer.measureText(template.displayText(it.text)).toDouble() }.toFloat() + space * (words.size - 1).coerceAtLeast(0)
            var x = (canvasWidth - lineWidth) / 2f
            val baseline = top + lineHeight * index - metrics.ascent
            words.forEach { word ->
                val wordWidth = measurer.measureText(template.displayText(word.text))
                layouts += CaptionWordLayout(word, index, x, baseline, wordWidth, lineHeight)
                x += wordWidth + space
            }
        }
        return CaptionLayout(layouts, lineHeight)
    }
}
