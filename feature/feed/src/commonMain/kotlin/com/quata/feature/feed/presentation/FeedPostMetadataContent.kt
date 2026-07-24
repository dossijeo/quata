package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Stable metadata header shared by feed cards, list rows and post previews.
 *
 * Avatar loading, profile navigation and contextual actions deliberately remain slots: those
 * concerns are implemented by the platform host, while commonMain owns the order and alignment
 * of the author identity and publication information.
 */
@Composable
fun FeedPostMetadataContent(
    displayName: String,
    publishedAt: String,
    modifier: Modifier = Modifier,
    avatar: (@Composable () -> Unit)? = null,
    authorAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        avatar?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (publishedAt.isNotBlank()) {
                Text(
                    text = publishedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        authorAction?.invoke()
    }
}
