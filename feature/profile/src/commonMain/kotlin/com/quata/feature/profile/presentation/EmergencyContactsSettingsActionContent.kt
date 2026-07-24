package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.CompactButtonContentPadding
import com.quata.core.ui.components.compactButtonMinSize

/** Shared SOS-settings entry action for a profile overview. */
@Composable
fun EmergencyContactsSettingsActionContent(
    label: String,
    selectedCount: Int,
    maxContacts: Int = 5,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .compactButtonMinSize(),
        shape = RoundedCornerShape(9.dp),
        contentPadding = CompactButtonContentPadding,
    ) {
        Text(label, fontWeight = FontWeight.ExtraBold)
        Text(
            text = "$selectedCount/$maxContacts",
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
