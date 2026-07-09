package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.R
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataThemeTemplate
import com.quata.core.designsystem.theme.quataTheme

data class QuataLiveRankingItem(
    val id: String,
    val rank: Int,
    val title: String,
    val subtitle: String,
    val avatarName: String,
    val avatarUrl: String?,
    val isOfficial: Boolean,
    val likesCount: Int
)

@Composable
fun QuataLiveRankingPanel(
    items: List<QuataLiveRankingItem>,
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit
) {
    val template = quataTheme()
    QuataStandardFloatingPanel(
        onDismiss = onDismiss,
        template = template
    ) { panelModifier, isLandscape ->
        Column(
            panelModifier.padding(
                start = 18.dp,
                top = if (isLandscape) 18.dp else 10.dp,
                end = 18.dp,
                bottom = if (isLandscape) 18.dp else 24.dp
            )
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.feed_live_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = stringResource(R.string.feed_live_subtitle),
                        color = template.colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
                CompactIconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .border(1.dp, template.colors.divider, RoundedCornerShape(16.dp))
                ) {
                    CompactIcon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = template.colors.textPrimary
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Surface(
                color = template.colors.surfaceAlt,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.feed_live_posts_monitored, items.size),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = stringResource(R.string.feed_live_updated),
                            color = template.colors.textSecondary
                        )
                    }
                    Surface(
                        color = template.colors.live,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Text(
                            text = stringResource(R.string.common_live),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    QuataLiveRankingRow(
                        item = item,
                        template = template,
                        onOpenItem = { onOpenItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuataLiveRankingRow(
    item: QuataLiveRankingItem,
    template: QuataThemeTemplate,
    onOpenItem: () -> Unit
) {
    val borderColor = when (item.rank) {
        1 -> template.colors.live
        2 -> template.colors.divider
        3 -> QuataOrange.copy(alpha = 0.8f)
        else -> template.colors.divider.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .background(template.colors.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${item.rank}",
            color = template.colors.live,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            modifier = Modifier.width(38.dp)
        )
        AvatarImage(
            name = item.avatarName,
            avatarUrl = item.avatarUrl,
            isOfficial = item.isOfficial,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.subtitle,
                color = template.colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.width(86.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\u2665", color = Color(0xFFFF5A8E), fontSize = 18.sp)
                Spacer(Modifier.width(4.dp))
                Text(item.likesCount.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Surface(
                color = template.colors.surfaceAlt,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .width(86.dp)
                    .height(38.dp)
                    .clickable(onClick = onOpenItem)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.feed_open_post), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
