package com.quata.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect

/**
 * BasicTextField owns IME, caret, selection and scroll. Its transparent glyph layout uses one
 * placeholder per emoji while the lower layer paints that same layout from common PNG resources.
 */
@Composable
actual fun QuataEmojiCommentTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    trailingIcon: @Composable () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val state = rememberTextFieldState(value.text, value.selection)
    val scrollState = rememberScrollState()
    val outputTransformation = remember { quataEmojiOutputTransformation() }
    val textStyle = LocalTextStyle.current

    LaunchedEffect(value.text, value.selection) {
        if (state.text.toString() != value.text || state.selection != value.selection) {
            state.edit {
                replace(0, length, value.text)
                selection = value.selection
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { TextFieldValue(state.text.toString(), state.selection) }.collect { next ->
            if (next != value) onValueChange(next)
        }
    }

    Row(
        modifier.fillMaxWidth().border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        leadingIcon()
        Box(Modifier.weight(1f).clipToBounds().padding(horizontal = 8.dp, vertical = 10.dp)) {
            if (value.text.isEmpty()) placeholder()
            if (value.text.isNotEmpty()) QuataEmojiStaticText(
                value = value.text,
                color = textStyle.color,
                fontSize = textStyle.fontSize,
                lineHeight = textStyle.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                fontWeight = textStyle.fontWeight,
                modifier = Modifier.graphicsLayer { translationX = -scrollState.value.toFloat() },
            )
            BasicTextField(
                state = state,
                enabled = enabled,
                onKeyboardAction = {},
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                outputTransformation = outputTransformation,
                scrollState = scrollState,
                textStyle = textStyle.copy(color = Color.Transparent),
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocused() },
            )
        }
        trailingIcon()
    }
}

private fun quataEmojiOutputTransformation(): OutputTransformation = OutputTransformation {
    var index = 0
    while (index < length) {
        val source = asCharSequence().toString()
        val emoji = emojiInlineEntryAt(source, index)?.first
        if (emoji == null) index += 1 else {
            replace(index, index + emoji.length, QuataEmojiPlaceholder.toString())
            index += 1
        }
    }
}
