package com.quata.feature.chat.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Portable SOS location-message body. Hosts localize labels and provide the map affordance icon. */
@Composable
fun ChatSosLocationContent(
    title: String,
    body: String?,
    locationLabel: String?,
    mapsUrl: String?,
    age: String?,
    accuracy: String?,
    speed: String?,
    isUpdate: Boolean,
    isUnavailable: Boolean,
    unavailableLabel: String,
    openMapsLabel: String,
    textColor: Color,
    accentColor: Color,
    onOpenMaps: (String) -> Unit,
    mapPreviewIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        body?.let { Text(it, color = textColor, fontSize = 14.sp) }
        locationLabel?.let { Text(it, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
        if (mapsUrl != null && isUpdate) {
            ChatSosLocationMapPreviewContent(icon = mapPreviewIcon)
        }
        if (age != null || accuracy != null || speed != null) {
            Surface(
                color = Color.White.copy(alpha = 0.30f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    age?.let { Text(it, color = textColor, fontSize = 13.sp) }
                    accuracy?.let { Text(it, color = textColor, fontSize = 13.sp) }
                    speed?.let { Text(it, color = textColor, fontSize = 13.sp) }
                }
            }
        } else if (isUnavailable) {
            Surface(
                color = Color.White.copy(alpha = 0.24f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    unavailableLabel,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        mapsUrl?.let { url ->
            Text(
                openMapsLabel,
                color = accentColor,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clickable { onOpenMaps(url) },
            )
        }
    }
}

@Composable
fun ChatSosLocationMapPreviewContent(
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFE8F0EA), Color(0xFFF6EFE5), Color(0xFFDDECF7)))),
        contentAlignment = Alignment.Center,
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp).offset(y = ((index - 1) * 18).dp)
                    .background(Color.White.copy(alpha = 0.72f)),
            )
        }
        repeat(3) { index ->
            Box(
                modifier = Modifier.width(1.dp).height(92.dp).offset(x = ((index - 1) * 42).dp)
                    .background(Color.White.copy(alpha = 0.62f)),
            )
        }
        icon()
    }
}
