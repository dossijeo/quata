package com.quata.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Portable prompt for a platform setting that needs user action. The platform host owns opening
 * its settings screen; this component only owns the dialog hierarchy and user choices.
 */
@Composable
fun QuataSettingsPromptDialogContent(
    title: String,
    body: String,
    openSettingsLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(openSettingsLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
