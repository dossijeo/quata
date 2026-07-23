package com.quata.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

enum class EmergencyContactsTab { Contacts, Message }

data class EmergencyContactsHeaderStrings(
    val back: String,
    val sos: String,
    val title: String,
    val description: String,
    val contactsTab: String,
    val messageTab: String
)

@Composable
fun EmergencyContactsHeaderContent(
    selectedTab: EmergencyContactsTab,
    strings: EmergencyContactsHeaderStrings,
    onTabSelected: (EmergencyContactsTab) -> Unit,
    onDismiss: () -> Unit
) {
    val template = quataTheme()
    Row(verticalAlignment = Alignment.CenterVertically) {
        CompactIconButton(onClick = onDismiss) {
            CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
        }
        Spacer(Modifier.width(6.dp))
        Surface(color = template.colors.sosSurface, shape = RoundedCornerShape(16.dp)) {
            Text(strings.sos, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Text(strings.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Text(strings.description, color = template.colors.textSecondary, lineHeight = 22.sp)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        EmergencyContactsTabButton(
            text = strings.contactsTab,
            selected = selectedTab == EmergencyContactsTab.Contacts,
            onClick = { onTabSelected(EmergencyContactsTab.Contacts) },
            modifier = Modifier.weight(1f)
        )
        EmergencyContactsTabButton(
            text = strings.messageTab,
            selected = selectedTab == EmergencyContactsTab.Message,
            onClick = { onTabSelected(EmergencyContactsTab.Message) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EmergencyContactsTabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = if (selected) template.colors.accent else template.colors.surfaceAlt,
        contentColor = if (selected) template.colors.accentContent else template.colors.textPrimary,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(48.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, fontWeight = FontWeight.ExtraBold) }
    }
}
