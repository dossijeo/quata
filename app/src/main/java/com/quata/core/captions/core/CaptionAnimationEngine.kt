package com.quata.core.captions.core

import com.quata.core.captions.templates.CaptionTemplateStyle
import kotlin.math.PI
import kotlin.math.sin

data class CaptionWordAnimation(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val translateY: Float = 0f
)

class CaptionAnimationEngine {
    fun animationFor(
        style: CaptionTemplateStyle,
        word: WordTiming,
        timeMs: Long
    ): CaptionWordAnimation {
        val startsIn = (timeMs - word.startMs).coerceAtLeast(0L).toFloat()
        val progress = (startsIn / PopDurationMs).coerceIn(0f, 1f)
        val eased = easeOutBack(progress)
        return when (style) {
            CaptionTemplateStyle.Karaoke -> CaptionWordAnimation()
            CaptionTemplateStyle.PopWord -> CaptionWordAnimation(
                alpha = 1f,
                scale = 0.72f + eased * 0.38f,
                translateY = (1f - progress) * 18f
            )
            CaptionTemplateStyle.Hormozi -> CaptionWordAnimation(
                scale = if (word.isActiveAt(timeMs)) 1.08f else 1f
            )
            CaptionTemplateStyle.Typewriter -> CaptionWordAnimation()
        }
    }

    private fun easeOutBack(value: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val p = value - 1f
        return 1f + c3 * p * p * p + c1 * p * p
    }
}

fun WordTiming.isActiveAt(timeMs: Long): Boolean = timeMs in startMs..endMs

fun WordTiming.progressAt(timeMs: Long): Float =
    ((timeMs - startMs).toFloat() / durationMs).coerceIn(0f, 1f)

fun CaptionSegment.typewriterText(timeMs: Long): String {
    val segmentDuration = (endMs - startMs).coerceAtLeast(1L)
    val progress = ((timeMs - startMs).toFloat() / segmentDuration).coerceIn(0f, 1f)
    val targetLength = (text.length * progress).toInt().coerceIn(0, text.length)
    val cursor = if (sin(progress * PI * 12).toFloat() > 0f) "|" else ""
    return text.take(targetLength).trimEnd() + cursor
}

private const val PopDurationMs = 260f
