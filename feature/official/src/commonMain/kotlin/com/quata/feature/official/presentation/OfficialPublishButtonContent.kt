package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange

/** Portable publish affordance for the Official editor. Submission remains owned by the host. */
@Composable
fun OfficialPublishButtonContent(
    enabled: Boolean,
    isPublishing: Boolean,
    publishLabel: String,
    publishingLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        enabled = enabled && !isPublishing,
        colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.White),
        onClick = onClick,
        modifier = modifier.height(52.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isPublishing) {
                LinearProgressIndicator(
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Text(publishingLabel, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(publishLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
