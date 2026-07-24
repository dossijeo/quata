package com.quata.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Shared comments-panel header; hosts inject actions that require navigation or platform capture. */
@Composable
fun QuataCommentsPanelHeaderContent(
    title: String,
    commentsCount: Int,
    trailingAction: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 66.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = template.colors.textSecondary,
            )
            Spacer(Modifier.width(10.dp))
            androidx.compose.material3.Text("\uD83D\uDCAC", fontSize = 16.sp)
            Spacer(Modifier.width(4.dp))
            androidx.compose.material3.Text(
                text = commentsCount.toString(),
                color = template.colors.textSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
        trailingAction(Modifier.align(Alignment.CenterEnd))
    }
}
