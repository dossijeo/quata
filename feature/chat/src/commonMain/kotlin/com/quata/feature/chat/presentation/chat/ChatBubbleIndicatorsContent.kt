package com.quata.feature.chat.presentation.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.MessageDeliveryState

@Composable
fun ChatTypingIndicatorContent(names: List<String>, modifier: Modifier = Modifier) {
    val template = quataTheme()
    val transition = rememberInfiniteTransition(label = "chat_typing_dots")
    val phase = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "chat_typing_phase").value
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(Modifier.background(template.colors.chatOther, RoundedCornerShape(20.dp)).border(1.dp, template.colors.divider, RoundedCornerShape(20.dp)).padding(horizontal = 17.dp, vertical = 13.dp)) {
            names.firstOrNull()?.takeIf { names.size == 1 }?.let { Text(it, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = template.colors.textSecondary, modifier = Modifier.padding(bottom = 7.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { index ->
                    val shifted = (phase + index * .22f) % 1f
                    val scale = .72f + .42f * (1f - kotlin.math.abs(shifted * 2f - 1f))
                    Box(Modifier.size(8.dp).graphicsLayer(scaleX = scale, scaleY = scale, alpha = .5f + scale * .45f).background(template.colors.textSecondary, CircleShape))
                }
            }
        }
    }
}

@Composable
fun ChatMessageDeliveryIndicatorContent(state: MessageDeliveryState, tint: Color, readTint: Color) {
    when (state) {
        MessageDeliveryState.Pending -> CircularProgressIndicator(Modifier.size(12.dp), color = tint.copy(alpha = .72f), strokeWidth = 1.7.dp)
        MessageDeliveryState.Failed -> Text("!", color = Color(0xFFD32F2F), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        MessageDeliveryState.Sent -> Text("✓", color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        MessageDeliveryState.Delivered, MessageDeliveryState.Read -> Text("✓✓", color = if (state == MessageDeliveryState.Read) readTint else tint, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
