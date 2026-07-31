package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.ui.components.QuataFeedEmoji
import com.quata.core.ui.components.QuataFeedEmojiIcon
import com.quata.core.designsystem.theme.quataTheme

@Composable
fun ReelScrimContent(showTopScrim: Boolean, modifier: Modifier = Modifier) {
    val stops = if (showTopScrim) arrayOf(0f to Color.Black.copy(alpha = .64f), .14f to Color.Black.copy(alpha = .42f), .34f to Color.Transparent, .58f to Color.Transparent, 1f to Color.Black.copy(alpha = .68f)) else arrayOf(0f to Color.Transparent, .58f to Color.Transparent, 1f to Color.Black.copy(alpha = .68f))
    androidx.compose.foundation.layout.Box(modifier.background(Brush.verticalGradient(*stops)))
}

@Composable
fun ReelTopChipsContent(documentText: String?, mediaBadgeText: String, isVideo: Boolean, locationLabel: @Composable (String) -> String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(
            start = 22.dp,
            end = 22.dp,
            top = ReelOverlayLayoutContract.topChipsOffset
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        mediaBadgeText.trim().takeIf { it.isNotBlank() }?.let { badge ->
            ReelFixedEmojiText(if (isVideo) "${QuataFeedEmoji.Note} $badge" else locationLabel(badge), Color.White, Modifier)
        }
        documentText?.let { ReelChipContent("${QuataFeedEmoji.Document} $it") }
    }
}

@Composable
fun ReelChipContent(text: String, highlighted: Boolean = false, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val template = quataTheme()
    val borderColor = if (highlighted) template.colors.live else Color.White.copy(alpha = .22f)
    val textColor = if (highlighted) template.colors.live else Color.White
    Surface(color = if (highlighted) template.colors.surface.copy(alpha = .74f) else Color.White.copy(alpha = .12f), contentColor = textColor, shape = RoundedCornerShape(28.dp), modifier = modifier.border(1.dp, borderColor, RoundedCornerShape(28.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        ReelFixedEmojiText(text, textColor, Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

@Composable
private fun ReelFixedEmojiText(value: String, color: Color, modifier: Modifier) {
    val fixed = listOf(QuataFeedEmoji.Sos, QuataFeedEmoji.Rank, QuataFeedEmoji.Location, QuataFeedEmoji.Note, QuataFeedEmoji.Document).firstOrNull { value.startsWith(it) }
    if (fixed == null) {
        Text(value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = modifier)
    } else Row(modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        QuataFeedEmojiIcon(fixed, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(value.removePrefix(fixed).removePrefix("\uFE0F").trimStart(), color = color, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
