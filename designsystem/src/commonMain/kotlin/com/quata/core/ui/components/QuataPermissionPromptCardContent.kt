package com.quata.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Portable permission prompt card; the platform host owns the actual permission launcher. */
@Composable
fun QuataPermissionPromptCardContent(
    message: String,
    actionLabel: String,
    actionAvailable: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                message,
                color = template.colors.textSecondary,
            )
            Button(onClick = onRequestPermission, enabled = actionAvailable) {
                Text(actionLabel)
            }
        }
    }
}
