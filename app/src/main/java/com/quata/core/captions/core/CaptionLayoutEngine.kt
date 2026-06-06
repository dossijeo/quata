package com.quata.core.captions.core

import android.graphics.Paint
import com.quata.core.captions.templates.CaptionTemplate

data class CaptionWordLayout(
    val word: WordTiming,
    val lineIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class CaptionLayout(
    val words: List<CaptionWordLayout>,
    val lineHeight: Float
)

class CaptionLayoutEngine {
    fun layout(
        segment: CaptionSegment,
        template: CaptionTemplate,
        paint: Paint,
        canvasWidth: Int,
        canvasHeight: Int
    ): CaptionLayout {
        val maxWidth = canvasWidth * template.maxWidthRatio
        val spaceWidth = paint.measureText(" ").coerceAtLeast(canvasWidth * 0.012f)
        val metrics = paint.fontMetrics
        val lineHeight = (metrics.descent - metrics.ascent) * template.lineHeightMultiplier
        val lines = mutableListOf<List<WordTiming>>()
        var currentLine = mutableListOf<WordTiming>()
        var currentWidth = 0f

        segment.words.forEach { word ->
            val wordWidth = paint.measureText(template.displayText(word.text))
            val nextWidth = if (currentLine.isEmpty()) wordWidth else currentWidth + spaceWidth + wordWidth
            if (currentLine.isNotEmpty() && nextWidth > maxWidth && lines.size < template.maxLines - 1) {
                lines += currentLine
                currentLine = mutableListOf()
                currentWidth = 0f
            }
            currentLine += word
            currentWidth = if (currentWidth == 0f) wordWidth else currentWidth + spaceWidth + wordWidth
        }
        if (currentLine.isNotEmpty()) lines += currentLine

        val visibleLines = lines.take(template.maxLines)
        val totalHeight = lineHeight * visibleLines.size
        val top = canvasHeight * template.verticalPosition - totalHeight / 2f
        val layouts = mutableListOf<CaptionWordLayout>()
        visibleLines.forEachIndexed { lineIndex, lineWords ->
            val lineWidth = lineWords.sumOf { paint.measureText(template.displayText(it.text)).toDouble() }.toFloat() +
                spaceWidth * (lineWords.size - 1).coerceAtLeast(0)
            var x = (canvasWidth - lineWidth) / 2f
            val baseline = top + lineHeight * lineIndex - metrics.ascent
            lineWords.forEach { word ->
                val displayText = template.displayText(word.text)
                val wordWidth = paint.measureText(displayText)
                layouts += CaptionWordLayout(
                    word = word,
                    lineIndex = lineIndex,
                    x = x,
                    y = baseline,
                    width = wordWidth,
                    height = lineHeight
                )
                x += wordWidth + spaceWidth
            }
        }
        return CaptionLayout(layouts, lineHeight)
    }
}
