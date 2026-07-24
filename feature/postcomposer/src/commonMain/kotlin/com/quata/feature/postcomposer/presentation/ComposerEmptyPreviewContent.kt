package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Portable empty-state frame used by image and video composer previews. */
@Composable
fun ComposerEmptyPreviewContent(
    title: String,
    tag: String,
    body: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 280.dp else 360.dp)
            .background(Color(0xFF101827), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
            .padding(if (compact) 20.dp else 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = if (compact) 32.sp else 44.sp,
            lineHeight = if (compact) 38.sp else 50.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 18.dp else 26.dp))
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
        ) {
            Text(tag, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
        Text(body, color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center)
    }
}
