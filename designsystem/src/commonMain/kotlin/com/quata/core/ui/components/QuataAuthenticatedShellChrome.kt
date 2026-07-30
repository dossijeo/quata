package com.quata.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme

/** Copy supplied by a platform host; the renderer remains entirely Compose common code. */
data class QuataAuthenticatedChromeStrings(
    val notifications: String,
    val offline: String,
    val sos: String,
)

/** Encoding-safe shared Spanish fallback used by native hosts without resource access. */
val QuataAuthenticatedChromeSpanish = QuataAuthenticatedChromeStrings(
    notifications = "Avisos",
    offline = "Sin conexi\u00f3n",
    sos = "SOS \ud83d\udea8",
)

/**
 * The one authenticated viewport contract shared by Android, Wasm and iOS.
 *
 * Its [content] receives the only padding calculation: physical safe top + 68dp chrome (+ an
 * optional 28dp offline banner) and the common 92dp bottom navigation with its bottom safe area.
 */
@Composable
fun QuataAuthenticatedShellChrome(
    notificationCount: Int,
    isNotificationBouncing: Boolean,
    isOnline: Boolean,
    strings: QuataAuthenticatedChromeStrings,
    onLogoClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSosClick: () -> Unit,
    isSosSending: Boolean,
    sosPulseScale: Float = 1f,
    showTopChrome: Boolean = true,
    modifier: Modifier = Modifier,
    bottomNavigation: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val template = quataTheme()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = template.colors.background,
        contentColor = template.colors.textPrimary,
        topBar = {
            if (showTopChrome) Column {
                QuataAuthenticatedTopChrome(
                    notificationCount = notificationCount,
                    isNotificationBouncing = isNotificationBouncing,
                    strings = strings,
                    onLogoClick = onLogoClick,
                    onNotificationsClick = onNotificationsClick,
                    onSosClick = onSosClick,
                    isSosSending = isSosSending,
                    sosPulseScale = sosPulseScale,
                )
                if (!isOnline) QuataOfflineBanner(strings.offline)
            }
        },
        bottomBar = bottomNavigation,
    ) { contentPadding ->
        content(contentPadding)
    }
}

@Composable
private fun QuataAuthenticatedTopChrome(
    notificationCount: Int,
    isNotificationBouncing: Boolean,
    strings: QuataAuthenticatedChromeStrings,
    onLogoClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSosClick: () -> Unit,
    isSosSending: Boolean,
    sosPulseScale: Float,
) {
    val template = quataTheme()
    val layoutDirection = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    Surface(
        color = template.colors.topChrome,
        contentColor = template.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().height(safe.calculateTopPadding() + AuthenticatedShellChromeContract.topChromeHeight),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = safe.calculateStartPadding(layoutDirection) + AuthenticatedShellChromeContract.headerHorizontalInset,
                    end = safe.calculateEndPadding(layoutDirection) + AuthenticatedShellChromeContract.headerHorizontalInset,
                    top = safe.calculateTopPadding() + AuthenticatedShellChromeContract.headerContentTopInset,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            QuataHeaderIdentity(
                notificationCount = notificationCount,
                isBouncing = isNotificationBouncing,
                notificationLabel = strings.notifications,
                onLogoClick = onLogoClick,
                onNotificationsClick = onNotificationsClick,
            )
            QuataSosButton(
                label = strings.sos,
                isSending = isSosSending,
                pulseScale = sosPulseScale,
                onClick = onSosClick,
            )
        }
    }
}

@Composable
private fun QuataHeaderIdentity(
    notificationCount: Int,
    isBouncing: Boolean,
    notificationLabel: String,
    onLogoClick: () -> Unit,
    onNotificationsClick: () -> Unit,
) {
    val template = quataTheme()
    val scale = if (isBouncing) {
        val transition = rememberInfiniteTransition(label = "notification_bounce")
        val value by transition.animateFloat(1f, 1.22f, infiniteRepeatable(tween(260), RepeatMode.Reverse), "notification_bounce_scale")
        value
    } else 1f
    Box(Modifier.size(width = 92.dp, height = AuthenticatedShellChromeContract.notificationsSize)) {
        val badgeShape = RoundedCornerShape(10.dp)
        Box(
            modifier = Modifier.size(AuthenticatedShellChromeContract.logoSize).shadow(
                elevation = 10.dp,
                shape = badgeShape,
                ambientColor = Color(0x40FF6A00),
                spotColor = Color(0x40FF6A00),
            )
                .background(Brush.linearGradient(listOf(Color(0xFFFF6A00), Color(0xFFFF7F1A))), badgeShape)
                .clickable(onClick = onLogoClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(QuataHeaderLogoGlyph, color = Color.White, fontFamily = quataHeaderLogoFontFamily(), fontWeight = FontWeight.Black, fontSize = 17.sp, lineHeight = 17.sp, letterSpacing = (-0.6).sp)
        }
        Box(
            modifier = Modifier.offset(x = AuthenticatedShellChromeContract.notificationsOffset).size(AuthenticatedShellChromeContract.notificationsSize).graphicsLayer { scaleX = scale; scaleY = scale }
                .clickable(onClick = onNotificationsClick),
            contentAlignment = Alignment.Center,
        ) {
            BadgedBox(badge = {
                if (notificationCount > 0) Badge(containerColor = template.colors.sos, modifier = Modifier.size(14.dp)) {
                    Text(notificationCount.coerceAtMost(99).toString(), color = Color.White, fontSize = template.textSizes.badge)
                }
            }) {
                Icon(Icons.Filled.Notifications, contentDescription = notificationLabel, tint = template.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun QuataSosButton(label: String, isSending: Boolean, pulseScale: Float, onClick: () -> Unit) {
    val template = quataTheme()
    Surface(
        color = template.colors.sos,
        contentColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.size(AuthenticatedShellChromeContract.sosWidth, AuthenticatedShellChromeContract.sosHeight).graphicsLayer {
            val scale = if (isSending) pulseScale else 1f
            scaleX = scale; scaleY = scale
        }.clickable(enabled = !isSending, onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            val inlineText = rememberQuataFeedEmojiInlineText(label)
            Text(inlineText.text, fontWeight = FontWeight.ExtraBold, fontSize = template.textSizes.caption, inlineContent = inlineText.inlineContent)
        }
    }
}

@Composable
private fun QuataOfflineBanner(label: String) {
    Surface(color = Color(0xFFB3261E), contentColor = Color.White, modifier = Modifier.fillMaxWidth().height(AuthenticatedShellChromeContract.offlineBannerHeight)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}
