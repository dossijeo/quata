package com.quata.core.ui.components

/** Shared text input styling. */

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun QuataTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    minLines: Int = 1,
    /** Platform-only input bridge; Compose remains responsible for every visible pixel. */
    inputOverlay: (@Composable (String, (String) -> Unit, (Boolean) -> Unit, Modifier) -> Unit)? = null,
    onOverlayFocusChanged: (Boolean) -> Unit = {},
    visualFocused: Boolean = false,
) {
    val template = quataTheme()
    val fieldModifier = if (singleLine && minLines == 1) {
        modifier.height(CompactTextFieldHeight)
    } else {
        modifier
    }
    Box(modifier = fieldModifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize().then(
                if (inputOverlay == null) Modifier else Modifier.clearAndSetSemantics { },
            ),
            singleLine = singleLine,
            minLines = minLines,
            placeholder = { Text(label) },
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = template.colors.surfaceAlt,
                unfocusedContainerColor = template.colors.surfaceAlt,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (visualFocused) MaterialTheme.colorScheme.primary else template.colors.inputBorder,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
        inputOverlay?.invoke(value, onValueChange, onOverlayFocusChanged, Modifier.fillMaxSize())
    }
}
