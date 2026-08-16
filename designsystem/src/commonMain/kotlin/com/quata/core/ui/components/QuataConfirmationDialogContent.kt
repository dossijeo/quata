package com.quata.core.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

const val QuataConfirmationDialogTestTag = "quata.confirmation.dialog"
const val QuataConfirmationDialogConfirmTestTag = "quata.confirmation.confirm"
const val QuataConfirmationDialogDismissTestTag = "quata.confirmation.dismiss"

/** Shared two-action confirmation overlay; hosts provide localized copy and side effects. */
@Composable
fun QuataConfirmationDialogContent(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.semantics { testTag = QuataConfirmationDialogTestTag },
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                modifier = Modifier.semantics { testTag = QuataConfirmationDialogConfirmTestTag },
                onClick = onConfirm,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.semantics { testTag = QuataConfirmationDialogDismissTestTag },
                onClick = onDismiss,
            ) { Text(dismissLabel) }
        },
    )
}
