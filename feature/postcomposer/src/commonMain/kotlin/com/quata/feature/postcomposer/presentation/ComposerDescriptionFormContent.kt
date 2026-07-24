package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared text-only description form used alongside platform-owned media acquisition and preview. */
@Composable
fun ComposerDescriptionFormContent(
    value: String,
    title: String,
    placeholder: String,
    minLines: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerSectionPanelContent(title = title, modifier = modifier, content = {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
        )
    })
}
