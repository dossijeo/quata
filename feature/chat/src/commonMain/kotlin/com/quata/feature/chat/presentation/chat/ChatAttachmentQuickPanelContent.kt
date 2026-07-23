package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon

data class ChatAttachmentQuickPanelStrings(val file: String, val gallery: String)

@Composable
fun ChatComposerModeBannerContent(text: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val template = quataTheme()
    Row(
        modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 12.dp)
            .background(template.colors.surface.copy(alpha = .92f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = onClear) { Text("X") }
    }
}

@Composable
fun ChatAttachmentQuickPanelContent(
    strings: ChatAttachmentQuickPanelStrings,
    onPickFile: () -> Unit,
    onPickGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(color = template.colors.surface.copy(alpha = .96f), shape = RoundedCornerShape(18.dp), shadowElevation = 4.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChatAttachmentQuickActionContent(Icons.AutoMirrored.Filled.InsertDriveFile, strings.file, onPickFile, Modifier.weight(1f))
            ChatAttachmentQuickActionContent(Icons.Filled.PhotoLibrary, strings.gallery, onPickGallery, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChatAttachmentQuickActionContent(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val template = quataTheme()
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(template.colors.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            CompactIcon(icon, contentDescription = null, tint = template.colors.accent)
        }
        Text(label, color = template.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
