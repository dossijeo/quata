package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Shared comment composer row; hosts retain authorization and comment persistence in onSend. */
@Composable
fun CommunityProfileCommentInputContent(
    value: String,
    placeholder: String,
    sendLabel: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 58.dp)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            enabled = value.isNotBlank(),
            onClick = onSend,
            colors = ButtonDefaults.buttonColors(
                containerColor = template.colors.accent,
                contentColor = template.colors.accentContent
            )
        ) {
            Text(sendLabel)
        }
    }
}
