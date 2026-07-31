package com.quata.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
                    AuthRequirement(requirement)
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

@Composable
private fun AuthRequirement(requirement: String) {
    if (!requirement.startsWith(AUTH_REQUIREMENT_CHECK_PREFIX)) {
        Text(requirement)
        return
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(requirement.removePrefix(AUTH_REQUIREMENT_CHECK_PREFIX).trimStart())
    }
}

private const val AUTH_REQUIREMENT_CHECK_PREFIX = "✓"
