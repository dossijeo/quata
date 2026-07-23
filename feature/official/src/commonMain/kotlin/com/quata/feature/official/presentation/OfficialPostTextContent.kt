package com.quata.feature.official.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.quataTheme
import com.quata.feature.official.domain.OfficialPostItem

@Composable fun OfficialTypePill(label: String, modifier: Modifier = Modifier) {
    val template = quataTheme()
    Surface(
        color = Color(0xFF2BA84A).copy(alpha = if (template.resolvedTheme == QuataResolvedTheme.Dark) .18f else .10f),
        contentColor = Color(0xFF2BA84A),
        shape = RoundedCornerShape(100.dp),
        modifier = modifier,
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
    }
}

@Composable fun OfficialPostTextBlock(post: OfficialPostItem, titleSize: Int, compact: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(post.title, fontWeight = FontWeight.Black, fontSize = titleSize.sp, lineHeight = (titleSize + 5).sp, maxLines = if (compact) 2 else 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp)); Box(Modifier.height(3.dp).width(54.dp).background(Color(0xFF2BA84A), RoundedCornerShape(20.dp))); Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
        Text(post.summary.ifBlank { post.contentPlain }, fontSize = if (compact) 14.sp else 16.sp, lineHeight = if (compact) 20.sp else 23.sp, maxLines = if (compact) 3 else 7, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun OfficialReadMoreLink(post: OfficialPostItem, label: String, onReadMore: () -> Unit, alignEnd: Boolean = false, modifier: Modifier = Modifier) {
    if (post.contentPlain.isBlank() || post.contentPlain.trim() == post.summary.trim()) if (post.linkUrl.isNullOrBlank()) return
    val template = quataTheme(); Spacer(Modifier.height(12.dp))
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) {
        Text(label, color = if (template.resolvedTheme == QuataResolvedTheme.Dark) Color(0xFF2EA7FF) else Color(0xFF17954B), fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onReadMore))
    }
}
