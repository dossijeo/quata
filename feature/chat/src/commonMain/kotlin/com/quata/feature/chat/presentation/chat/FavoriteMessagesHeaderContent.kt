package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon
import com.quata.core.ui.components.CompactIconButton

/** Shared header for the favorite-messages view. */
@Composable
fun FavoriteMessagesHeaderContent(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface.copy(alpha = 0.92f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactIconButton(onClick = onBack) {
                CompactIcon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(template.colors.accent.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CompactIcon(Icons.Filled.Star, contentDescription = null, tint = template.colors.accent)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.ExtraBold)
        }
    }
}
