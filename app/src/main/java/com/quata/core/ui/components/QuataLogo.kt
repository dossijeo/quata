package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataOrange

@Composable
fun QuataLogo(modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .background(QuataOrange, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Q", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.height(14.dp))
        Text("Qüata", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
