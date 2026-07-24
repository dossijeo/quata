package com.quata.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quata.core.designsystem.theme.quataTheme
import kotlinx.coroutines.delay

/**
 * Portable full-screen media overlay shell.
 *
 * The host owns loading and rendering the actual media through [mediaContent], so Android,
 * browser and iOS can use their native image/video implementations without duplicating the
 * overlay transition, chrome and dismissal behaviour.
 */
@Composable
fun QuataFullscreenMediaOverlayContent(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    mediaContent: @Composable (Modifier) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasOpened = true
        visible = true
    }

    Dialog(
        onDismissRequest = { visible = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.18f) + fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF05070C))
                    .navigationBarsPadding(),
            ) {
                QuataFullscreenMediaOverlayTopBar(
                    title = title,
                    onBack = { visible = false },
                )
                mediaContent(
                    with(this@Column) {
                        Modifier.weight(1f).fillMaxWidth()
                    },
                )
            }
        }
    }

    LaunchedEffect(visible, hasOpened) {
        if (hasOpened && !visible) {
            delay(170L)
            onDismiss()
        }
    }
}

@Composable
private fun QuataFullscreenMediaOverlayTopBar(
    title: String,
    onBack: () -> Unit,
) {
    val template = quataTheme()
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .zIndex(2f)
            .background(template.colors.topChrome)
            .padding(horizontal = 8.dp),
    ) {
        CompactIconButton(onClick = onBack) {
            CompactIcon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = template.colors.textPrimary,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            color = template.colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
