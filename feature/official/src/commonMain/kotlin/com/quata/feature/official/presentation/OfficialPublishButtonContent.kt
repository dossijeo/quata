package com.quata.feature.official.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val clickEnabled = enabled && !isPublishing
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(if (clickEnabled || isPublishing) QuataOrange else QuataOrange.copy(alpha = 0.45f))
            .semantics { role = Role.Button }
            .clickable(enabled = clickEnabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPublishing) {
            LinearProgressIndicator(
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Text(publishingLabel, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Text(publishLabel, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
