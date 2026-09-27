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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

object QuataAccountLifecycleTestTags {
    const val Dialog = "account-lifecycle.dialog"
    const val Password = "account-lifecycle.password"
    const val Confirmation = "account-lifecycle.confirmation"
    const val Cancel = "account-lifecycle.cancel"
    const val Confirm = "account-lifecycle.confirm"
    const val Error = "account-lifecycle.error"
    const val Progress = "account-lifecycle.progress"
}

internal fun accountLifecycleConfirmationEnabled(
    password: String,
    confirmation: String,
    requiredConfirmation: String?,
    isWorking: Boolean,
): Boolean = !isWorking && password.isNotBlank() &&
    (requiredConfirmation == null || confirmation.trim().equals(requiredConfirmation, ignoreCase = true))

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
    val canConfirm = accountLifecycleConfirmationEnabled(password, confirmation, requiredConfirmation, isWorking)
    AlertDialog(
        modifier = Modifier.testTag(QuataAccountLifecycleTestTags.Dialog)
            .semantics { contentDescription = QuataAccountLifecycleTestTags.Dialog },
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
                    modifier = Modifier.fillMaxWidth().testTag(QuataAccountLifecycleTestTags.Password)
                        .semantics { contentDescription = QuataAccountLifecycleTestTags.Password },
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
                        modifier = Modifier.fillMaxWidth().testTag(QuataAccountLifecycleTestTags.Confirmation)
                            .semantics { contentDescription = QuataAccountLifecycleTestTags.Confirmation },
                    )
                }
                errorMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color.Red, modifier = Modifier.testTag(QuataAccountLifecycleTestTags.Error)
                        .semantics { contentDescription = QuataAccountLifecycleTestTags.Error })
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isWorking,
                onClick = onDismiss,
                modifier = Modifier.testTag(QuataAccountLifecycleTestTags.Cancel)
                    .semantics { contentDescription = QuataAccountLifecycleTestTags.Cancel },
            ) { Text(cancelLabel) }
        },
        confirmButton = {
            TextButton(
                enabled = canConfirm,
                onClick = { onConfirm(password) },
                modifier = Modifier.testTag(QuataAccountLifecycleTestTags.Confirm)
                    .semantics { contentDescription = QuataAccountLifecycleTestTags.Confirm },
            ) {
                if (isWorking) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp).testTag(QuataAccountLifecycleTestTags.Progress)
                            .semantics { contentDescription = QuataAccountLifecycleTestTags.Progress },
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(confirmLabel)
                }
            }
        },
    )
}
