package com.quata.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.quata.core.designsystem.theme.quataTheme
import kotlinx.coroutines.delay

const val QuataFullscreenMediaOverlayRootTestTag = "fullscreen-media.root"
const val QuataFullscreenMediaOverlayBackTestTag = "fullscreen-media.back"
const val QuataFullscreenMediaOverlayCloseTestTag = "fullscreen-media.close"
const val QuataFullscreenMediaOverlayMediaCloseTestTag = "fullscreen-media.media-close"
const val QuataFullscreenMediaOverlayTitleTestTag = "fullscreen-media.title"

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
    nativeClose: @Composable BoxScope.(onDismiss: () -> Unit) -> Unit = {},
    mediaContent: @Composable (Modifier) -> Unit,
) {
    val template = quataTheme()
    var visible by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasOpened = true
        visible = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(50f),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(initialScale = 0.18f) + fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(QuataFullscreenMediaOverlayRootTestTag)
                    .semantics { contentDescription = QuataFullscreenMediaOverlayRootTestTag }
                    .background(Color(0xFF05070C))
                    .navigationBarsPadding(),
            ) {
                QuataFullscreenMediaOverlayTopBar(
                    title = title,
                    onBack = onDismiss,
                )
                Box(
                    modifier = with(this@Column) {
                        Modifier.weight(1f).fillMaxWidth()
                    },
                ) {
                    mediaContent(Modifier.fillMaxSize())
                    CompactIconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .zIndex(6f)
                            .padding(16.dp)
                            .background(template.colors.topChrome.copy(alpha = 0.88f), CircleShape)
                            .testTag(QuataFullscreenMediaOverlayMediaCloseTestTag)
                            .semantics { contentDescription = QuataFullscreenMediaOverlayMediaCloseTestTag },
                    ) {
                        CompactIcon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = template.colors.textPrimary,
                        )
                    }
                    nativeClose(onDismiss)
                }
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
            .clickable(onClick = onBack)
            .padding(horizontal = 8.dp),
    ) {
        CompactIconButton(
            onClick = onBack,
            modifier = Modifier
                .testTag(QuataFullscreenMediaOverlayBackTestTag)
                .semantics { contentDescription = QuataFullscreenMediaOverlayBackTestTag },
        ) {
            CompactIcon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = template.colors.textPrimary,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .testTag(QuataFullscreenMediaOverlayTitleTestTag)
                .semantics { contentDescription = QuataFullscreenMediaOverlayTitleTestTag },
            color = template.colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
        CompactIconButton(
            onClick = onBack,
            modifier = Modifier
                .testTag(QuataFullscreenMediaOverlayCloseTestTag)
                .semantics { contentDescription = QuataFullscreenMediaOverlayCloseTestTag },
        ) {
            CompactIcon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = template.colors.textPrimary,
            )
        }
    }
}
