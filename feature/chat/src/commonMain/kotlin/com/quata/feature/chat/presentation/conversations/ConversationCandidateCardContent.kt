package com.quata.feature.chat.presentation.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Shared candidate-row structure; the host supplies the platform-backed avatar slot. */
@Composable
fun ConversationCandidateCardContent(
    title: String,
    subtitle: String,
    isOpening: Boolean,
    actionIcon: ImageVector,
    actionContentDescription: String,
    isSelected: Boolean,
    onToggleSelection: (() -> Unit)?,
    onOpen: () -> Unit,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    actionTestTag: String? = null,
) {
    val template = quataTheme()
    val isSelectionMode = onToggleSelection != null
    Surface(
        color = template.colors.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider, RoundedCornerShape(18.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isSelectionMode) { onToggleSelection?.invoke() }
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection?.invoke() })
                    Spacer(Modifier.size(8.dp))
                }
                avatar()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isSelectionMode) 112.dp else 58.dp,
                        end = 52.dp
                    )
            ) {
                Text(title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = template.colors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val actionModifier = actionTestTag?.let { tag ->
                Modifier.testTag(tag).semantics { contentDescription = "$tag $actionContentDescription" }
            } ?: Modifier
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(template.colors.accent)
                    .clickable(enabled = !isOpening, role = Role.Button, onClick = onOpen)
                    .then(actionModifier),
                contentAlignment = Alignment.Center,
            ) {
                if (isOpening) {
                    CircularProgressIndicator(
                        color = template.colors.accentContent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(actionIcon, contentDescription = actionContentDescription, tint = template.colors.accentContent)
                }
            }
        }
    }
}
