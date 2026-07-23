package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon
import com.quata.feature.neighborhoods.domain.NeighborhoodUser

data class NeighborhoodUserRowStrings(val follow: String, val following: String, val chat: String)

@Composable
fun NeighborhoodUserRowContent(
    user: NeighborhoodUser,
    isOwnUser: Boolean,
    isFollowingLoading: Boolean,
    isOpeningChat: Boolean,
    strings: NeighborhoodUserRowStrings,
    avatar: @Composable () -> Unit,
    onFollowUser: () -> Unit,
    onOpenPrivateChat: () -> Unit
) {
    val template = quataTheme()
    Column(Modifier.fillMaxWidth().background(template.colors.surface.copy(alpha = 0.42f), RoundedCornerShape(18.dp)).border(1.dp, template.colors.divider, RoundedCornerShape(18.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            avatar()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.neighborhood, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onFollowUser, enabled = !isOwnUser && !isFollowingLoading, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), colors = ButtonDefaults.buttonColors(containerColor = template.colors.accent, contentColor = template.colors.accentContent)) {
                if (isFollowingLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = template.colors.accentContent) else CompactIcon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (user.isFollowing) strings.following else strings.follow, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
            }
            OutlinedButton(onClick = onOpenPrivateChat, enabled = !isOwnUser && !isOpeningChat, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = template.colors.accent)) {
                if (isOpeningChat) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = template.colors.accent) else CompactIcon(Icons.AutoMirrored.Filled.Message, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(strings.chat, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}
