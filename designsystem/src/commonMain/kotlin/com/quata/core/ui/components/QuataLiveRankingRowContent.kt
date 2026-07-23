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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun QuataLiveRankingRowContent(
    item: QuataLiveRankingItem,
    openLabel: String,
    avatar: @Composable () -> Unit,
    onOpenItem: () -> Unit
) {
    val template = quataTheme()
    val borderColor = when (item.rank) { 1 -> template.colors.live; 2 -> template.colors.divider; 3 -> QuataOrange.copy(alpha = .8f); else -> template.colors.divider.copy(alpha = .7f) }
    Row(Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(20.dp)).background(template.colors.surface, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("#${item.rank}", color = template.colors.live, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.width(38.dp))
        avatar(); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle, color = template.colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.width(86.dp), horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("♥", color = Color(0xFFFF5A8E), fontSize = 18.sp); Spacer(Modifier.width(4.dp)); Text(item.likesCount.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
            Spacer(Modifier.height(8.dp))
            Surface(color = template.colors.surfaceAlt, shape = RoundedCornerShape(14.dp), modifier = Modifier.width(86.dp).height(38.dp).clickable(onClick = onOpenItem)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(openLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
        }
    }
}
