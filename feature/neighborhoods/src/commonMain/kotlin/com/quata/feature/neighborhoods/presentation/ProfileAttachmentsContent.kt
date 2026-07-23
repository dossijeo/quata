package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quata.feature.neighborhoods.domain.ProfileAttachment

data class ProfileAttachmentsStrings(
    val title: String,
    val empty: String
)

@Composable
fun ProfileAttachmentsContent(
    attachments: List<ProfileAttachment>,
    strings: ProfileAttachmentsStrings,
    attachmentItem: @Composable (ProfileAttachment) -> Unit
) {
    Column {
        Text(strings.title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        if (attachments.isEmpty()) {
            Text(strings.empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                attachments.forEach { attachment -> attachmentItem(attachment) }
            }
        }
    }
}
