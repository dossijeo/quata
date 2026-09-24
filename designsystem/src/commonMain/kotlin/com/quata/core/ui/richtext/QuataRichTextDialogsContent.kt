package com.quata.core.ui.richtext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Portable heading-level selector. The launcher supplies localized labels and
 * owns the editor state update through [onSelect].
 */
@Composable
fun QuataRichTextHeadingDialogContent(
    current: Int,
    title: String,
    normalTextLabel: (currentLevel: Int) -> String,
    closeLabel: String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag("quata-portable-rich-text-heading-dialog"),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (level in 1..6) {
                    DropdownMenuItem(
                        text = { Text("H$level") },
                        onClick = { onSelect(level) },
                        modifier = Modifier.testTag("quata-portable-rich-text-heading-$level"),
                    )
                }
                DropdownMenuItem(
                    text = { Text(normalTextLabel(current)) },
                    onClick = { onSelect(0) },
                    modifier = Modifier.testTag("quata-portable-rich-text-heading-normal"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("quata-portable-rich-text-heading-close"),
            ) { Text(closeLabel) }
        },
        dismissButton = {},
    )
}

/** Portable URL editor used by the rich-text host. */
@Composable
fun QuataRichTextLinkDialogContent(
    initialUrl: String,
    title: String,
    placeholder: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }

    AlertDialog(
        modifier = Modifier.testTag("quata-portable-rich-text-link-dialog"),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text(placeholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quata-portable-rich-text-link-url"),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url) },
                modifier = Modifier.testTag("quata-portable-rich-text-link-apply"),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("quata-portable-rich-text-link-cancel"),
            ) { Text(dismissLabel) }
        },
    )
}
