package com.quata.core.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** The object-replacement character has the same 1em width as the inline atlas placeholder. */
internal const val QuataEmojiPlaceholder = '\uFFFC'

/**
 * Replaces each known emoji grapheme with one layout character while retaining UTF-16 selection
 * offsets for the original Unicode value. ZWJ, flag and variation sequences each occupy one cell.
 */
internal class QuataEmojiVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val visual = quataEmojiVisualText(text.text)
        return TransformedText(AnnotatedString(visual.text), visual.offsetMapping)
    }
}

internal class QuataEmojiVisualText internal constructor(
    val text: String,
    val offsetMapping: OffsetMapping,
)

internal fun quataEmojiVisualText(value: String): QuataEmojiVisualText {
    val originalToTransformed = IntArray(value.length + 1)
    val transformedToOriginal = ArrayList<Int>(value.length + 1)
    val output = StringBuilder(value.length)
    var original = 0
    var transformed = 0
    transformedToOriginal += 0
    while (original < value.length) {
        val emoji = emojiInlineEntryAt(value, original)?.first
        if (emoji == null) {
            originalToTransformed[original] = transformed
            output.append(value[original])
            original += 1
            transformed += 1
            transformedToOriginal += original
        } else {
            val end = original + emoji.length
            for (offset in original until end) originalToTransformed[offset] = transformed
            originalToTransformed[end] = transformed + 1
            output.append(QuataEmojiPlaceholder)
            original = end
            transformed += 1
            transformedToOriginal += original
        }
    }
    originalToTransformed[value.length] = transformed
    return QuataEmojiVisualText(
        text = output.toString(),
        offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, value.length)]
            override fun transformedToOriginal(offset: Int): Int = transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
        },
    )
}
