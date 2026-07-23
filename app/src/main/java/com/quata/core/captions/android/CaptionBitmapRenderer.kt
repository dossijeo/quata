package com.quata.core.captions.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import com.quata.core.captions.core.CaptionAnimationEngine
import com.quata.core.captions.core.CaptionDocument
import com.quata.core.captions.core.CaptionFontMetrics
import com.quata.core.captions.core.CaptionLayoutCalculator
import com.quata.core.captions.core.CaptionLayoutTemplate
import com.quata.core.captions.core.CaptionSegment
import com.quata.core.captions.core.CaptionTextMeasurer
import com.quata.core.captions.core.WordTiming
import com.quata.core.captions.core.isActiveAt
import com.quata.core.captions.core.typewriterText
import com.quata.core.captions.templates.CaptionTemplate
import com.quata.core.captions.templates.CaptionTemplateStyle
import com.quata.core.captions.templates.CaptionTemplates

class CaptionBitmapRenderer(
    private val layoutEngine: CaptionLayoutCalculator = CaptionLayoutCalculator(),
    private val animationEngine: CaptionAnimationEngine = CaptionAnimationEngine()
) {
    fun renderFrame(
        document: CaptionDocument,
        style: CaptionTemplateStyle,
        timeMs: Long,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val segment = document.segmentAt(timeMs) ?: return bitmap
        renderSegment(canvas, segment, CaptionTemplates.get(style), timeMs, width, height)
        return bitmap
    }

    private fun renderSegment(
        canvas: Canvas,
        segment: CaptionSegment,
        template: CaptionTemplate,
        timeMs: Long,
        width: Int,
        height: Int
    ) {
        val textPaint = baseTextPaint(template, width)
        if (template.style == CaptionTemplateStyle.Typewriter) {
            renderTypewriter(canvas, segment, template, textPaint, timeMs, width, height)
            return
        }
        if (template.style == CaptionTemplateStyle.PopWord) {
            renderPopWord(canvas, segment, template, textPaint, timeMs, width, height)
            return
        }

        val layout = layoutEngine.layout(
            segment = segment,
            template = CaptionLayoutTemplate(template.maxWidthRatio, template.maxLines, template.verticalPosition, template.lineHeightMultiplier, template.uppercase),
            measurer = object : CaptionTextMeasurer {
                override fun measureText(text: String): Float = textPaint.measureText(text)
                override val fontMetrics: CaptionFontMetrics
                    get() = textPaint.fontMetrics.let { CaptionFontMetrics(it.ascent, it.descent) }
            },
            canvasWidth = width,
            canvasHeight = height,
        )
        if (layout.words.isEmpty()) return
        renderSegmentBackground(canvas, template, layout.words.minOf { it.x }, layout.words.maxOf { it.x + it.width }, layout.words.minOf { it.y - layout.lineHeight }, layout.words.maxOf { it.y }, width)

        layout.words.forEach { wordLayout ->
            val active = wordLayout.word.isActiveAt(timeMs)
            val animation = animationEngine.animationFor(template.style, wordLayout.word, timeMs)
            val displayText = template.displayText(wordLayout.word.text)
            val centerX = wordLayout.x + wordLayout.width / 2f
            val baseline = wordLayout.y + animation.translateY
            canvas.save()
            canvas.scale(animation.scale, animation.scale, centerX, baseline - wordLayout.height / 2f)
            renderWordBackground(canvas, template, wordLayout.word, timeMs, wordLayout.x, baseline, wordLayout.width, wordLayout.height, width)
            drawTextWithStroke(
                canvas = canvas,
                text = displayText,
                x = wordLayout.x,
                y = baseline,
                fillColor = if (active) template.activeTextColor else template.textColor,
                strokeColor = template.strokeColor,
                strokeWidth = width * template.strokeWidthRatio,
                paint = textPaint,
                alpha = animation.alpha
            )
            canvas.restore()
        }
    }

    private fun renderPopWord(
        canvas: Canvas,
        segment: CaptionSegment,
        template: CaptionTemplate,
        paint: TextPaint,
        timeMs: Long,
        width: Int,
        height: Int
    ) {
        val activeWord = segment.words.firstOrNull { it.isActiveAt(timeMs) } ?: segment.words.firstOrNull() ?: return
        val animation = animationEngine.animationFor(template.style, activeWord, timeMs)
        val text = template.displayText(activeWord.text)
        val textWidth = paint.measureText(text)
        val metrics = paint.fontMetrics
        val x = (width - textWidth) / 2f
        val baseline = height * template.verticalPosition + animation.translateY
        val centerX = width / 2f
        val centerY = baseline + (metrics.ascent + metrics.descent) / 2f
        canvas.save()
        canvas.scale(animation.scale, animation.scale, centerX, centerY)
        renderWordBackground(
            canvas = canvas,
            template = template,
            word = activeWord,
            timeMs = timeMs,
            x = x,
            baseline = baseline,
            wordWidth = textWidth,
            wordHeight = metrics.descent - metrics.ascent,
            outputWidth = width
        )
        drawTextWithStroke(
            canvas = canvas,
            text = text,
            x = x,
            y = baseline,
            fillColor = template.activeTextColor,
            strokeColor = template.strokeColor,
            strokeWidth = width * template.strokeWidthRatio,
            paint = paint,
            alpha = animation.alpha
        )
        canvas.restore()
    }

    private fun renderTypewriter(
        canvas: Canvas,
        segment: CaptionSegment,
        template: CaptionTemplate,
        paint: TextPaint,
        timeMs: Long,
        width: Int,
        height: Int
    ) {
        val text = template.displayText(segment.typewriterText(timeMs))
        if (text.isBlank()) return
        val textWidth = paint.measureText(text).coerceAtMost(width * template.maxWidthRatio)
        val metrics = paint.fontMetrics
        val boxPaddingX = width * 0.034f
        val boxPaddingY = width * 0.018f
        val x = (width - textWidth) / 2f
        val baseline = height * template.verticalPosition
        val box = RectF(
            x - boxPaddingX,
            baseline + metrics.ascent - boxPaddingY,
            x + textWidth + boxPaddingX,
            baseline + metrics.descent + boxPaddingY
        )
        template.segmentBackgroundColor?.let {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = it
                style = Paint.Style.FILL
            }.also { backgroundPaint ->
                canvas.drawRoundRect(box, width * template.cornerRadiusRatio, width * template.cornerRadiusRatio, backgroundPaint)
            }
        }
        drawTextWithStroke(canvas, text, x, baseline, template.textColor, template.strokeColor, width * template.strokeWidthRatio, paint, 1f)
    }

    private fun renderSegmentBackground(
        canvas: Canvas,
        template: CaptionTemplate,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        width: Int
    ) {
        val color = template.segmentBackgroundColor ?: return
        val padding = width * 0.026f
        val rect = RectF(minX - padding, minY - padding, maxX + padding, maxY + padding)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, width * template.cornerRadiusRatio, width * template.cornerRadiusRatio, paint)
    }

    private fun renderWordBackground(
        canvas: Canvas,
        template: CaptionTemplate,
        word: WordTiming,
        timeMs: Long,
        x: Float,
        baseline: Float,
        wordWidth: Float,
        wordHeight: Float,
        outputWidth: Int
    ) {
        val active = word.isActiveAt(timeMs)
        val backgroundColor = if (active) template.activeBackgroundColor else null
        if (backgroundColor == null) return
        val paddingX = outputWidth * 0.014f
        val paddingY = outputWidth * 0.008f
        val rect = RectF(
            x - paddingX,
            baseline - wordHeight + paddingY,
            x + wordWidth + paddingX,
            baseline + paddingY
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, outputWidth * template.cornerRadiusRatio, outputWidth * template.cornerRadiusRatio, paint)
    }

    private fun drawTextWithStroke(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        fillColor: Int,
        strokeColor: Int,
        strokeWidth: Float,
        paint: TextPaint,
        alpha: Float
    ) {
        val alphaInt = (alpha.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
        if (strokeWidth > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.color = strokeColor
            paint.alpha = alphaInt
            canvas.drawText(text, x, y, paint)
        }
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 0f
        paint.color = fillColor
        paint.alpha = alphaInt
        canvas.drawText(text, x, y, paint)
        paint.alpha = 255
    }

    private fun baseTextPaint(template: CaptionTemplate, width: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = template.typeface
            textSize = width * template.textSizeRatio
            color = template.textColor
            textAlign = Paint.Align.LEFT
            if (template.shadowRadiusRatio > 0f) {
                setShadowLayer(width * template.shadowRadiusRatio, 0f, width * 0.004f, template.strokeColor)
            } else {
                clearShadowLayer()
            }
        }
}
