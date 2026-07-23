package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.ui.components.CompactIcon

data class ProfileActionStrings(val follow: String, val following: String, val chat: String)

@Composable
fun ProfilePrimaryActions(
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    isFollowingLoading: Boolean,
    isOpeningChat: Boolean,
    strings: ProfileActionStrings,
    onFollow: () -> Unit,
    onChat: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onFollow, enabled = !isOwnProfile && !isFollowingLoading, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = QuataOrange, contentColor = Color.Black), modifier = Modifier.weight(1f)) {
            if (isFollowingLoading) CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
            else CompactIcon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp)); Text(if (isFollowing) strings.following else strings.follow, fontSize = 18.sp)
        }
        OutlinedButton(onClick = onChat, enabled = !isOwnProfile && !isOpeningChat, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = QuataOrange), modifier = Modifier.weight(1f)) {
            if (isOpeningChat) CircularProgressIndicator(Modifier.size(18.dp), color = QuataOrange, strokeWidth = 2.dp)
            else CompactIcon(Icons.AutoMirrored.Filled.Message, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp)); Text(strings.chat, fontSize = 18.sp)
        }
    }
}
