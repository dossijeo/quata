package com.quata.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

const val QuataUgcTermsDialogTestTag = "quata-ugc-terms-dialog"
const val QuataUgcTermsBodyTestTag = "quata-ugc-terms-body"
const val QuataUgcTermsAcceptTestTag = "quata-ugc-terms-accept"
const val QuataUgcTermsLogoutTestTag = "quata-ugc-terms-logout"
const val QuataUgcTermsErrorTestTag = "quata-ugc-terms-error"

/**
 * Non-dismissible terms acceptance prompt. The legal-document links are injected because their
 * opening mechanism belongs to each platform host.
 */
@Composable
fun QuataTermsAcceptanceDialogContent(
    title: String,
    body: String,
    acceptLabel: String,
    acceptingLabel: String,
    logoutLabel: String,
    errorMessage: String? = null,
    isAccepting: Boolean,
    onAccept: () -> Unit,
    onLogout: () -> Unit,
    legalLinks: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag(QuataUgcTermsDialogTestTag),
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(body, modifier = Modifier.testTag(QuataUgcTermsBodyTestTag))
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(QuataUgcTermsErrorTestTag),
                    )
                }
                Spacer(Modifier.height(8.dp))
                legalLinks()
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isAccepting,
                onClick = onLogout,
                modifier = Modifier.testTag(QuataUgcTermsLogoutTestTag),
            ) { Text(logoutLabel) }
        },
        confirmButton = {
            TextButton(
                enabled = !isAccepting,
                onClick = onAccept,
                modifier = Modifier.testTag(QuataUgcTermsAcceptTestTag),
            ) {
                Text(if (isAccepting) acceptingLabel else acceptLabel)
            }
        },
    )
}
