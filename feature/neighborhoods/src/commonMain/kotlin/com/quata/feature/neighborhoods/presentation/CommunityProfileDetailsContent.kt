package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared scrollable body for a community profile sheet/screen.
 *
 * Hosts retain modal presentation, attachment viewers, navigation and media playback. This
 * container only orders the already-portable header, attachments and optional gallery regions.
 */
@Composable
fun CommunityProfileDetailsContent(
    listState: LazyListState,
    header: @Composable () -> Unit,
    attachments: @Composable () -> Unit,
    gallery: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 28.dp),
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
    ) {
        item(key = "community-profile-header") { header() }
        item(key = "community-profile-attachments") { attachments() }
        gallery?.let { content ->
            item(key = "community-profile-gallery") { content() }
        }
    }
}
