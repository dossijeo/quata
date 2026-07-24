package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared responsive shell for media composers. Media acquisition, rendering and editing remain
 * platform slots while the common presentation layer owns the controls/preview/publish layout.
 */
@Composable
fun ComposerMediaPostFormLayoutContent(
    isLandscapeLayout: Boolean,
    controls: @Composable ColumnScope.() -> Unit,
    preview: @Composable ColumnScope.() -> Unit,
    publish: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    if (isLandscapeLayout) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                controls()
                publish()
            }
            Column(Modifier.weight(1f)) { preview() }
        }
    } else {
        Column(modifier) {
            controls()
            preview()
            publish()
        }
    }
}
