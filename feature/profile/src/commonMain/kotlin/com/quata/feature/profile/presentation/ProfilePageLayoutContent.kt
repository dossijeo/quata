package com.quata.feature.profile.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared responsive scroll shell for profile account pages. */
@Composable
fun ProfilePageLayoutContent(
    isLandscapeLayout: Boolean,
    scrollState: ScrollState,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = if (isLandscapeLayout) 8.dp else 14.dp,
                top = if (isLandscapeLayout) 10.dp else 12.dp,
                end = 14.dp,
                bottom = 12.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}
