package com.quata.feature.neighborhoods.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

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
    val actionTag = action.testTagSuffix()
    AlertDialog(
        modifier = Modifier.semantics {
            testTag = PublicProfileModerationDialogTestTagPrefix + actionTag
            contentDescription = PublicProfileModerationDialogTestTagPrefix + actionTag
        },
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
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    testTag = PublicProfileModerationDialogCancelTestTag
                    contentDescription = PublicProfileModerationDialogCancelTestTag
                },
            ) {
                Text(strings.cancel)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(action) },
                modifier = Modifier.semantics {
                    testTag = PublicProfileModerationDialogConfirmTestTagPrefix + actionTag
                    contentDescription = PublicProfileModerationDialogConfirmTestTagPrefix + actionTag
                },
            ) {
                Text(when (action) {
                    ProfileModerationAction.Report -> strings.report
                    ProfileModerationAction.Block -> strings.block
                    ProfileModerationAction.Unblock -> strings.unblock
                })
            }
        }
    )
}

private fun ProfileModerationAction.testTagSuffix(): String = when (this) {
    ProfileModerationAction.Report -> "report"
    ProfileModerationAction.Block -> "block"
    ProfileModerationAction.Unblock -> "unblock"
}
