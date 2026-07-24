package com.quata.feature.profile.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.QuataPanel

/** Shared profile-overview account card; avatar and platform-bound action column are slots. */
@Composable
fun ProfileOverviewAccountCardContent(
    avatar: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    QuataPanel(contentPadding = PaddingValues(12.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            avatar()
            Spacer(Modifier.width(12.dp))
            actions()
        }
    }
}
