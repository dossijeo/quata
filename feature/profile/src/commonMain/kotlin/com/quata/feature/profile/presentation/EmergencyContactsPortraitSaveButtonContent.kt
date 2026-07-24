package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme

/** Shared primary action for the portrait SOS editor. */
@Composable
fun EmergencyContactsPortraitSaveButtonContent(
    label: String,
    isSaving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Button(
        onClick = onSave,
        enabled = !isSaving,
        colors = ButtonDefaults.buttonColors(
            containerColor = template.colors.accent,
            contentColor = template.colors.accentContent
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().height(54.dp)
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold)
    }
}
