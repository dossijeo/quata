package com.quata.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Portable About dialog; hosts inject legal-link actions and navigation destinations. */
@Composable
fun QuataAboutDialogContent(
    title: String,
    version: String,
    versionDate: String,
    body: String,
    releaseHistoryLabel: String,
    closeLabel: String,
    onDismiss: () -> Unit,
    onOpenReleaseHistory: () -> Unit,
    legalLinks: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(version)
                Spacer(Modifier.height(8.dp))
                Text(versionDate)
                Spacer(Modifier.height(12.dp))
                Text(body)
                Spacer(Modifier.height(12.dp))
                legalLinks()
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onOpenReleaseHistory) { Text(releaseHistoryLabel) }
                TextButton(onClick = onDismiss) { Text(closeLabel) }
            }
        },
    )
}
