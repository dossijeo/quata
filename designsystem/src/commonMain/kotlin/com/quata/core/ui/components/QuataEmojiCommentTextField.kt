package com.quata.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

/** Common comment editor; Wasm keeps native Compose editing and draws catalog cells from the atlas. */
@Composable
expect fun QuataEmojiCommentTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    trailingIcon: @Composable () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
