package com.quata.core.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun QuataAlertDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null
) {
    val template = quataTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = template.colors.surface,
        titleContentColor = template.colors.textPrimary,
        textContentColor = template.colors.textSecondary,
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = { Text(message) },
        confirmButton = { QuataPrimaryButton(confirmLabel, onClick = onConfirm) },
        dismissButton = dismissLabel?.let { label ->
            { QuataSecondaryButton(label, onClick = onDismiss) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuataBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val template = quataTheme()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = template.colors.surface,
        contentColor = template.colors.textPrimary,
        content = content
    )
}
