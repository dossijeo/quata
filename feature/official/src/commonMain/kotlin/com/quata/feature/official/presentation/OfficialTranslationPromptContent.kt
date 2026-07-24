package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared translation confirmation/progress dialog. The loader is injected by the host. */
@Composable
fun OfficialTranslationPromptContent(
    title: String,
    message: String,
    progressLabel: String,
    confirmLabel: String,
    skipLabel: String,
    isTranslating: Boolean,
    loader: @Composable () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onGenerate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            if (isTranslating) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    loader()
                    Text(progressLabel, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(enabled = !isTranslating, onClick = onGenerate) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isTranslating, onClick = onSkip) {
                Text(skipLabel)
            }
        },
    )
}
