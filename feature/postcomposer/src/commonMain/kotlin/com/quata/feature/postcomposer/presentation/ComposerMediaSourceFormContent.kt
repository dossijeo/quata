package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared source-selection form for image and video posts. System pickers, camera launchers,
 * media names and editors are supplied by the platform, while common presentation owns the
 * responsive panel and the selected-media action hierarchy.
 */
@Composable
fun ComposerMediaSourceFormContent(
    title: String,
    isLandscapeLayout: Boolean,
    primarySourceAction: @Composable (Modifier) -> Unit,
    secondarySourceAction: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    beforeEdit: (@Composable ColumnScope.() -> Unit)? = null,
    editAction: (@Composable (Modifier) -> Unit)? = null,
    afterEdit: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ComposerSectionPanelContent(
        title = title,
        highlighted = true,
        content = {
        if (isLandscapeLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                primarySourceAction(Modifier.fillMaxWidth())
                secondarySourceAction(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                primarySourceAction(Modifier.weight(1f))
                secondarySourceAction(Modifier.weight(1f))
            }
        }
        beforeEdit?.let {
            Spacer(Modifier.height(12.dp))
            it(this)
        }
        editAction?.let { action ->
            Spacer(Modifier.height(12.dp))
            action(Modifier.fillMaxWidth())
        }
        afterEdit?.let {
            Spacer(Modifier.height(12.dp))
            it(this)
        }
        },
        modifier = modifier,
    )
}
