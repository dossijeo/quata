package com.quata.core.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataDivider
import com.quata.core.designsystem.theme.QuataSurfaceAlt

@Composable
fun QuataTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        label = { Text(label) },
        shape = RoundedCornerShape(18.dp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = QuataSurfaceAlt,
            unfocusedContainerColor = QuataSurfaceAlt,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = QuataDivider,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}
