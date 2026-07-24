package com.quata.feature.externalshare

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Shared progress/error state used when an external payload targets one conversation directly. */
@Composable
fun ExternalShareSendingStateContent(
    message: String,
    isSending: Boolean,
    error: String?,
    closeLabel: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { Text(error ?: message) },
        text = {
            if (isSending) CircularProgressIndicator()
        },
        confirmButton = {
            if (!isSending && error != null) {
                TextButton(onClick = onDismiss) { Text(closeLabel) }
            }
        }
    )
}
