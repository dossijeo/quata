package com.quata.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

data class QuataLiveRankingStrings(
    val title: String,
    val subtitle: String,
    val monitoredPosts: String,
    val updated: String,
    val live: String,
    val close: String,
    val openPost: String
)

@Composable
fun QuataLiveRankingPanelContent(
    items: List<QuataLiveRankingItem>,
    isLandscape: Boolean,
    strings: QuataLiveRankingStrings,
    avatar: @Composable (QuataLiveRankingItem) -> Unit,
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val template = quataTheme()
    Column(modifier.padding(start = 18.dp, top = if (isLandscape) 18.dp else 10.dp, end = 18.dp, bottom = if (isLandscape) 18.dp else 24.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(strings.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(strings.subtitle, color = template.colors.textSecondary, fontSize = 14.sp)
            }
            CompactIconButton(onClick = onDismiss, modifier = Modifier.size(48.dp).border(1.dp, template.colors.divider, RoundedCornerShape(16.dp))) {
                CompactIcon(Icons.Filled.Close, strings.close, tint = template.colors.textPrimary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(color = template.colors.surfaceAlt, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings.monitoredPosts, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(strings.updated, color = template.colors.textSecondary)
                }
                Surface(color = template.colors.live, contentColor = Color.White, shape = CircleShape) {
                    Text(strings.live, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { item ->
                QuataLiveRankingRowContent(item, strings.openPost, avatar = { avatar(item) }, onOpenItem = { onOpenItem(item.id) })
            }
        }
    }
}
