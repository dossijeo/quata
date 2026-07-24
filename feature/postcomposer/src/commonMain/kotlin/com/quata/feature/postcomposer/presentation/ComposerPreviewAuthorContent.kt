package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Shared author/description overlay. Avatar rendering is injected by the platform host. */
@Composable
fun ComposerPreviewAuthorContent(
    description: String,
    authorName: String,
    subtitle: String,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(modifier = modifier.fillMaxWidth()) {
        description.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
            Spacer(Modifier.height(9.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            avatar()
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = authorName,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = template.colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
