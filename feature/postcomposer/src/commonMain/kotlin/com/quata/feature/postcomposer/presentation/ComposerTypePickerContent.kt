package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.CompactIcon

data class ComposerTypePickerStrings(val text: String, val image: String, val video: String)

@Composable
fun ComposerTypePickerContent(
    isLandscapeLayout: Boolean,
    strings: ComposerTypePickerStrings,
    onText: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit
) {
    val types = listOf(
        ComposerTypeItem(strings.text, Icons.Filled.Edit, onText),
        ComposerTypeItem(strings.image, Icons.Filled.PhotoCamera, onImage),
        ComposerTypeItem(strings.video, Icons.Filled.Videocam, onVideo)
    )
    if (isLandscapeLayout) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            types.forEach { type ->
                ComposerTypeCard(type.label, type.icon, type.onClick, Modifier.weight(1f), iconAboveText = true)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            types.forEach { type -> ComposerTypeCard(type.label, type.icon, type.onClick) }
        }
    }
}

private data class ComposerTypeItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
private fun ComposerTypeCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconAboveText: Boolean = false
) {
    val template = quataTheme()
    Surface(
        color = template.colors.surface,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().height(128.dp)
            .border(1.dp, template.colors.divider, RoundedCornerShape(24.dp)).clickable(onClick = onClick)
    ) {
        if (iconAboveText) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ComposerTypeIcon(icon, 62.dp, 28.dp)
                Spacer(Modifier.height(10.dp))
                Text(label.uppercase(), color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp, lineHeight = 16.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                ComposerTypeIcon(icon, 82.dp, 34.dp)
                Spacer(Modifier.width(20.dp))
                Text(label.uppercase(), color = template.colors.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = template.textSizes.title)
            }
        }
    }
}

@Composable
private fun ComposerTypeIcon(icon: ImageVector, containerSize: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp) {
    val template = quataTheme()
    Box(
        modifier = Modifier.size(containerSize).border(1.dp, template.colors.selectedBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) { CompactIcon(icon, contentDescription = null, tint = template.colors.accent, modifier = Modifier.size(iconSize)) }
}
