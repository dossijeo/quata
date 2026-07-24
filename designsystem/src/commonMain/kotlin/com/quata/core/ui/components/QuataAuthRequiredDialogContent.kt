package com.quata.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Portable auth-required prompt. Hosts supply localized copy and retain
 * responsibility for navigation/auth launchers through callbacks.
 */
@Composable
fun QuataAuthRequiredDialogContent(
    title: String,
    intro: String,
    requirements: List<String>,
    outro: String,
    createAccountLabel: String,
    loginLabel: String,
    onDismiss: () -> Unit,
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                Text(intro)
                Spacer(Modifier.height(12.dp))
                for (requirement in requirements) {
                    Text(requirement)
                }
                Spacer(Modifier.height(12.dp))
                Text(outro)
            }
        },
        dismissButton = {
            TextButton(onClick = onCreateAccount) { Text(createAccountLabel) }
        },
        confirmButton = {
            TextButton(onClick = onLogin) { Text(loginLabel) }
        },
    )
}
