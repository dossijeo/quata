package com.quata.feature.neighborhoods.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

enum class ProfileModerationAction { Report, Block }

data class ProfileModerationConfirmationStrings(
    val reportTitle: String,
    val blockTitle: String,
    val reportMessage: String,
    val blockMessage: String,
    val cancel: String,
    val report: String,
    val block: String
)

@Composable
fun ProfileModerationConfirmation(
    action: ProfileModerationAction?,
    strings: ProfileModerationConfirmationStrings,
    onDismiss: () -> Unit,
    onConfirm: (ProfileModerationAction) -> Unit
) {
    action ?: return
    val isBlock = action == ProfileModerationAction.Block
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isBlock) strings.blockTitle else strings.reportTitle) },
        text = { Text(if (isBlock) strings.blockMessage else strings.reportMessage) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(action) }) {
                Text(if (isBlock) strings.block else strings.report)
            }
        }
    )
}
