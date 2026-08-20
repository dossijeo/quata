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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
const val ComposerDestinationLoadingTestTag = "composer-destination-loading"
const val ComposerDestinationErrorTestTag = "composer-destination-error"
const val ComposerDestinationEmptyTestTag = "composer-destination-empty"
const val ComposerDestinationRetryTestTag = "composer-destination-retry"

@Composable
fun ComposerDestinationSelectorContent(
    title: String,
    helper: String,
    destinations: List<PostComposerDestination>,
    selectedDestination: PostComposerDestination?,
    loading: Boolean,
    errorMessage: String?,
    emptyMessage: String,
    loadingMessage: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onDestinationSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerSectionPanelContent(
        title = title,
        modifier = modifier.testTag(ComposerDestinationSelectorTestTag),
        content = {
            when {
                loading -> ComposerDestinationStatusContent(
                    message = loadingMessage,
                    testTag = ComposerDestinationLoadingTestTag,
                    leading = { CircularProgressIndicator() },
                )
                errorMessage != null -> ComposerDestinationStatusContent(
                    message = errorMessage,
                    testTag = ComposerDestinationErrorTestTag,
                    trailing = {
                        TextButton(onClick = onRetry, modifier = Modifier.testTag(ComposerDestinationRetryTestTag)) {
                            Text(retryLabel)
                        }
                    },
                )
                destinations.isEmpty() -> ComposerDestinationStatusContent(
                    message = emptyMessage,
                    testTag = ComposerDestinationEmptyTestTag,
                    trailing = {
                        TextButton(onClick = onRetry, modifier = Modifier.testTag(ComposerDestinationRetryTestTag)) {
                            Text(retryLabel)
                        }
                    },
                )
                else -> ComposerDestinationOptionsContent(helper, destinations, selectedDestination, onDestinationSelected)
            }
        },
    )
}

@Composable
private fun ComposerDestinationStatusContent(
    message: String,
    testTag: String,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .semantics { contentDescription = testTag },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(
                text = message,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun ComposerDestinationOptionsContent(
    helper: String,
    destinations: List<PostComposerDestination>,
    selectedDestination: PostComposerDestination?,
    onDestinationSelected: (String) -> Unit,
) {
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
