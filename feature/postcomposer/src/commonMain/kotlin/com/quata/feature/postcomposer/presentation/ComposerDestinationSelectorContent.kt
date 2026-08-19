package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.feature.postcomposer.domain.PostComposerDestination

const val ComposerDestinationSelectorTestTag = "composer-destination-selector"
const val ComposerDestinationSelectedTestTag = "composer-destination-selected"

@Composable
fun ComposerDestinationSelectorContent(
    title: String,
    helper: String,
    destinations: List<PostComposerDestination>,
    selectedDestination: PostComposerDestination?,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destinations.isEmpty()) return
    ComposerSectionPanelContent(
        title = title,
        modifier = modifier.testTag(ComposerDestinationSelectorTestTag),
        content = {
            Text(
                text = selectedDestination?.label ?: helper,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag(ComposerDestinationSelectedTestTag),
            )
            Text(
                text = selectedDestination?.subtitle ?: helper,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                destinations.forEach { destination ->
                    ComposerDestinationOptionContent(
                        destination = destination,
                        selected = destination.wallId == selectedDestination?.wallId,
                        onClick = { onDestinationSelected(destination.wallId) },
                    )
                }
            }
        },
    )
}

@Composable
private fun ComposerDestinationOptionContent(
    destination: PostComposerDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val template = quataTheme()
    val color = if (selected) template.colors.surfaceRaised else template.colors.surface
    Surface(
        color = color,
        contentColor = template.colors.textPrimary,
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) template.colors.selectedBorder else template.colors.divider, RoundedCornerShape(9.dp))
            .testTag("composer-destination-option.${destination.wallId}")
            .semantics(mergeDescendants = true) {
                role = Role.Button
                this.selected = selected
                contentDescription = "Destino: ${destination.label} composer-destination-option.${destination.wallId}"
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Public, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(destination.label, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                destination.subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(it, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
