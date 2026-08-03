package com.quata.feature.neighborhoods.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

enum class ProfileModerationAction { Report, Block, Unblock }

data class ProfileModerationConfirmationStrings(
    val reportTitle: String,
    val blockTitle: String,
    val unblockTitle: String,
    val reportMessage: String,
    val blockMessage: String,
    val unblockMessage: String,
    val cancel: String,
    val report: String,
    val block: String,
    val unblock: String,
)

@Composable
fun ProfileModerationConfirmation(
    action: ProfileModerationAction?,
    strings: ProfileModerationConfirmationStrings,
    onDismiss: () -> Unit,
    onConfirm: (ProfileModerationAction) -> Unit
) {
    action ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(when (action) {
                ProfileModerationAction.Report -> strings.reportTitle
                ProfileModerationAction.Block -> strings.blockTitle
                ProfileModerationAction.Unblock -> strings.unblockTitle
            })
        },
        text = {
            Text(when (action) {
                ProfileModerationAction.Report -> strings.reportMessage
                ProfileModerationAction.Block -> strings.blockMessage
                ProfileModerationAction.Unblock -> strings.unblockMessage
            })
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(action) }) {
                Text(when (action) {
                    ProfileModerationAction.Report -> strings.report
                    ProfileModerationAction.Block -> strings.block
                    ProfileModerationAction.Unblock -> strings.unblock
                })
            }
        }
    )
}
