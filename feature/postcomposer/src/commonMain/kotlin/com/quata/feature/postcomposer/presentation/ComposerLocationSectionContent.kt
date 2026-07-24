package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Shared location editor structure; platform hosts inject icon, CTA and system-aware text input. */
@Composable
fun ComposerLocationSectionContent(
    title: String,
    locationText: String,
    helperText: String,
    isHighlighted: Boolean,
    leadingIcon: @Composable () -> Unit,
    editAction: @Composable (Modifier) -> Unit,
    editor: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            leadingIcon()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 88.dp)
            ) {
                androidx.compose.material3.Text(
                    text = title,
                    color = template.colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
                androidx.compose.material3.Text(
                    text = locationText,
                    color = template.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            editAction(Modifier.align(Alignment.CenterEnd))
        }
        editor?.let {
            Spacer(Modifier.height(12.dp))
            it()
        }
        androidx.compose.material3.Text(
            text = helperText,
            color = if (isHighlighted) template.colors.accent else template.colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
