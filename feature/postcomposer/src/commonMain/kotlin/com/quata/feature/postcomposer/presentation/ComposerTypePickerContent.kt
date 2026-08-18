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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.accessibility.CriticalControlsAccessibilityCopy
import com.quata.core.ui.components.CompactIcon
import com.quata.feature.postcomposer.domain.PostComposerType

data class ComposerTypePickerStrings(val text: String, val image: String, val video: String)

@Composable
fun ComposerTypePickerContent(
    isLandscapeLayout: Boolean,
    strings: ComposerTypePickerStrings,
    selectedType: PostComposerType? = null,
    accessibility: CriticalControlsAccessibilityCopy? = null,
    enabled: Boolean = true,
    onText: () -> Unit,
    onImage: () -> Unit,
    onVideo: () -> Unit
) {
    val types = listOf(
        ComposerTypeItem(strings.text, Icons.Filled.Edit, PostComposerType.Text, onText),
        ComposerTypeItem(strings.image, Icons.Filled.PhotoCamera, PostComposerType.Image, onImage),
        ComposerTypeItem(strings.video, Icons.Filled.Videocam, PostComposerType.Video, onVideo)
    )
    val focusRequesters = remember { List(types.size) { FocusRequester() } }
    val traversalGroupModifier = Modifier
        .testTag("composer-type-picker")
        .semantics { isTraversalGroup = true }
    if (isLandscapeLayout) {
        Row(
            Modifier.fillMaxWidth().then(traversalGroupModifier),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            types.forEachIndexed { index, type ->
                ComposerTypeCard(
                    label = type.label,
                    icon = type.icon,
                    type = type.type,
                    selected = type.type == selectedType,
                    enabled = enabled,
                    traversalIndex = index.toFloat(),
                    focusRequester = focusRequesters[index],
                    previousFocusRequester = focusRequesters.getOrNull(index - 1),
                    nextFocusRequester = focusRequesters.getOrNull(index + 1),
                    onClick = type.onClick,
                    accessibility = accessibility,
                    modifier = Modifier.weight(1f),
                    iconAboveText = true,
                )
            }
        }
    } else {
        Column(
            Modifier.fillMaxWidth().then(traversalGroupModifier),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            types.forEachIndexed { index, type ->
                ComposerTypeCard(
                    label = type.label,
                    icon = type.icon,
                    type = type.type,
                    selected = type.type == selectedType,
                    enabled = enabled,
                    traversalIndex = index.toFloat(),
                    focusRequester = focusRequesters[index],
                    previousFocusRequester = focusRequesters.getOrNull(index - 1),
                    nextFocusRequester = focusRequesters.getOrNull(index + 1),
                    onClick = type.onClick,
                    accessibility = accessibility,
                )
            }
        }
    }
}

private data class ComposerTypeItem(val label: String, val icon: ImageVector, val type: PostComposerType, val onClick: () -> Unit)

@Composable
private fun ComposerTypeCard(
    label: String,
    icon: ImageVector,
    type: PostComposerType,
    selected: Boolean,
    enabled: Boolean,
    traversalIndex: Float,
    focusRequester: FocusRequester,
    previousFocusRequester: FocusRequester?,
    nextFocusRequester: FocusRequester?,
    onClick: () -> Unit,
    accessibility: CriticalControlsAccessibilityCopy?,
    modifier: Modifier = Modifier,
    iconAboveText: Boolean = false
) {
    val template = quataTheme()
    var focused by remember { mutableStateOf(false) }
    val focusModifier = Modifier
        .focusRequester(focusRequester)
        .focusProperties {
            previousFocusRequester?.let { previous = it }
            nextFocusRequester?.let { next = it }
        }
        .onFocusChanged { focused = it.isFocused }
    val accessibilityModifier = if (accessibility != null) {
        val control = accessibility.composerType
        Modifier
            .testTag("composer-type-${type.name.lowercase()}")
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
                if (!enabled) disabled()
                this.traversalIndex = traversalIndex
                contentDescription = "${control.name}: $label composer-type-${type.name.lowercase()}"
                stateDescription = "${control.state(selected, isEnabled = enabled)}; ${control.focus(focused)}"
            }
    } else {
        Modifier
    }
    Surface(
        color = template.colors.surface,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth().height(128.dp)
            .border(1.dp, template.colors.divider, RoundedCornerShape(24.dp))
            .then(focusModifier)
            .then(accessibilityModifier)
            .clickable(enabled = enabled, onClick = onClick)
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
