package com.quata.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
fun ClickableProfileAvatar(
    name: String,
    avatarUrl: String?,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(44.dp)
) {
    ProfileAvatarWithLoadingHalo(
        name = name,
        avatarUrl = avatarUrl,
        isLoading = isLoading,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
}

@Composable
fun ProfileAvatarWithLoadingHalo(
    name: String,
    avatarUrl: String?,
    isLoading: Boolean,
    modifier: Modifier = Modifier.size(44.dp)
) {
    val rotation = if (isLoading) {
        val transition = rememberInfiniteTransition(label = "profile_avatar_loading")
        val animatedRotation = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "profile_avatar_loading_rotation"
        )
        animatedRotation.value
    } else {
        0f
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isLoading) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(
                        scaleX = 1.16f,
                        scaleY = 1.16f,
                        rotationZ = rotation
                    )
            ) {
                val radius = size.minDimension / 2f
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFE5D45C),
                            Color(0xFFE5D45C).copy(alpha = 0.72f),
                            Color.Transparent,
                            Color(0xFFE5D45C).copy(alpha = 0.18f),
                            Color.Transparent,
                            Color(0xFFE5D45C)
                        ),
                        center = center
                    ),
                    radius = radius,
                    center = center
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE5D45C).copy(alpha = 0.24f), Color.Transparent),
                        center = center,
                        radius = radius * 0.42f
                    ),
                    radius = radius,
                    center = center
                )
            }
        }
        AvatarImage(
            name = name,
            avatarUrl = avatarUrl,
            modifier = Modifier.matchParentSize()
        )
    }
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
