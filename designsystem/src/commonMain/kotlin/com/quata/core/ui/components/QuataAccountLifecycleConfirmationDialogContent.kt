package com.quata.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Portable confirmation form for account deactivation/deletion. Host code owns the lifecycle
 * operation and localized text, while the shared layer owns validation and dialog structure.
 */
@Composable
fun QuataAccountLifecycleConfirmationDialogContent(
    title: String,
    body: String,
    passwordPrompt: String,
    passwordLabel: String,
    cancelLabel: String,
    confirmLabel: String,
    isWorking: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit,
    confirmationPrompt: String? = null,
    requiredConfirmation: String? = null,
) {
    var confirmation by remember(title, requiredConfirmation) { mutableStateOf("") }
    var password by remember(title, requiredConfirmation) { mutableStateOf("") }
    val requiresConfirmation = requiredConfirmation != null
    val canConfirm = !isWorking && password.isNotBlank() &&
        (!requiresConfirmation || confirmation.trim().equals(requiredConfirmation, ignoreCase = true))
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !isWorking,
            dismissOnClickOutside = !isWorking,
        ),
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(body)
                Spacer(Modifier.height(12.dp))
                Text(passwordPrompt)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !isWorking,
                    singleLine = true,
                    label = { Text(passwordLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requiresConfirmation) {
                    Spacer(Modifier.height(12.dp))
                    Text(confirmationPrompt.orEmpty())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        enabled = !isWorking,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color.Red)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isWorking, onClick = onDismiss) { Text(cancelLabel) }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = { onConfirm(password) }) {
                if (isWorking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(confirmLabel)
                }
            }
        },
    )
}
