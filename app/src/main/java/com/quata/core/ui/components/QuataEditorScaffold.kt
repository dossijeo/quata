package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.R
import com.quata.core.designsystem.theme.quataTheme
import androidx.compose.ui.res.stringResource

@Composable
fun QuataEditorScaffold(
    title: String,
    showTitle: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    bottomPadding: Dp = 0.dp,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val template = quataTheme()
    Surface(
        modifier = modifier.fillMaxSize(),
        color = template.colors.background,
        contentColor = template.colors.textPrimary
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = bottomPadding)
        ) {
            QuataEditorTopBar(
                title = title,
                showTitle = showTitle,
                backEnabled = backEnabled,
                onBack = onBack,
                actions = actions
            )
            content()
        }
    }
}

@Composable
private fun QuataEditorTopBar(
    title: String,
    showTitle: Boolean,
    backEnabled: Boolean,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val template = quataTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(template.colors.surfaceRaised)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIconButton(onClick = onBack, enabled = backEnabled) {
            CompactIcon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.video_editor_back),
                tint = template.colors.textPrimary
            )
        }
        Spacer(Modifier.width(6.dp))
        if (showTitle) {
            Text(
                text = title,
                color = template.colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
        actions()
    }
}

@Composable
fun QuataEditorToolButton(
    label: String,
    enabled: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val template = quataTheme()
    val contentAlpha = if (enabled) 1f else 0.42f
    val iconColor = if (selected) template.colors.accentContent else template.colors.textPrimary.copy(alpha = contentAlpha)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 66.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) template.colors.accent else template.colors.surfaceAlt.copy(alpha = if (enabled) 1f else 0.54f))
                .border(1.dp, if (selected) template.colors.accent else template.colors.divider, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides iconColor) {
                Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = template.colors.textSecondary.copy(alpha = contentAlpha),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
