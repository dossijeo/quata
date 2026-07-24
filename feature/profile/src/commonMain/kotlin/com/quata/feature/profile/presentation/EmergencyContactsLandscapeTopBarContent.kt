package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

/** Shared top action bar for the landscape SOS editor. */
@Composable
fun EmergencyContactsLandscapeTopBarContent(
    backLabel: String,
    sosLabel: String,
    title: String,
    saveLabel: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CompactIconButton(onClick = onDismiss) {
            CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
        }
        Spacer(Modifier.width(6.dp))
        Surface(color = template.colors.sosSurface, shape = RoundedCornerShape(16.dp)) {
            Text(sosLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onSave,
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.height(42.dp).width(196.dp)
        ) {
            Text(saveLabel, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
