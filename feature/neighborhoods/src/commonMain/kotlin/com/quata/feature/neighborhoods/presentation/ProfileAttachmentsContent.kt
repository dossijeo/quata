package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quata.feature.neighborhoods.domain.ProfileAttachment

const val PublicProfileAttachmentsTestTag = "public-profile.attachments"
const val PublicProfileAttachmentsEmptyTestTag = "public-profile.attachments.empty"
const val PublicProfileAttachmentItemTestTagPrefix = "public-profile.attachments.item."

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
    Column(Modifier.semantics { testTag = PublicProfileAttachmentsTestTag }) {
        Text(strings.title, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(10.dp))
        if (attachments.isEmpty()) {
            Text(
                strings.empty,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { testTag = PublicProfileAttachmentsEmptyTestTag },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                attachments.forEach { attachment ->
                    Column(Modifier.semantics { testTag = PublicProfileAttachmentItemTestTagPrefix + attachment.id }) {
                        attachmentItem(attachment)
                    }
                }
            }
        }
    }
}
