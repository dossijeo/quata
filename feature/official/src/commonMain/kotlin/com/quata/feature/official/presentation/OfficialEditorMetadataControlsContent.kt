package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

data class OfficialEditorSelectionOption(val id: String, val label: String)

/** State-hoisted dropdown field used by the type and read-more controls in the Official editor. */
@Composable
fun OfficialEditorDropdownFieldContent(
    selectedLabel: String,
    options: List<OfficialEditorSelectionOption>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { onExpandedChange(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(selectedLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onOptionSelected(option.id)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

@Composable
fun OfficialEditorLinkFieldContent(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
}
