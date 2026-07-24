package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared pager-page spacing for Official feed cards; the host supplies the actual card. */
@Composable
fun OfficialPagerPostPageContent(
    card: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(horizontal = 14.dp)) {
        card(Modifier.fillMaxSize().padding(bottom = 10.dp))
    }
}
