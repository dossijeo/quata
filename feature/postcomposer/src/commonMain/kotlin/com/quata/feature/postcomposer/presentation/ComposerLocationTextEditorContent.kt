package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.designsystem.theme.quataTheme

/** Portable manual-location editor; geocoding, permissions and location acquisition stay hosted. */
@Composable
fun ComposerLocationTextEditorContent(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = template.colors.textPrimary,
            unfocusedTextColor = template.colors.textPrimary,
            focusedBorderColor = template.colors.accent,
            unfocusedBorderColor = template.colors.divider,
            cursorColor = template.colors.accent,
        ),
    )
}
