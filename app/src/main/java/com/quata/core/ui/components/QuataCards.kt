package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.quata.core.designsystem.theme.QuataOrange
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.model.User

@Composable
fun QuataCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = QuataSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

@Composable
fun AvatarLetter(name: String, modifier: Modifier = Modifier.size(44.dp)) {
    val letter = name.trim().firstOrNull()?.uppercase() ?: "Q"
    Box(
        modifier = modifier
            .background(QuataOrange, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
fun AvatarImage(
    name: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier.size(44.dp)
) {
    if (avatarUrl.isNullOrBlank()) {
        AvatarLetter(name, modifier)
    } else {
        AsyncImage(
            model = avatarUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(CircleShape)
                .border(1.dp, QuataOrange.copy(alpha = 0.42f), CircleShape)
        )
    }
}

@Composable
fun UserAvatar(user: User, modifier: Modifier = Modifier.size(44.dp)) {
    AvatarImage(
        name = user.displayName,
        avatarUrl = user.avatarUrl,
        modifier = modifier
    )
}

@Composable
fun UserHeader(name: String, meta: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        AvatarLetter(name)
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column {
            Text(name, fontWeight = FontWeight.Bold)
            Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
