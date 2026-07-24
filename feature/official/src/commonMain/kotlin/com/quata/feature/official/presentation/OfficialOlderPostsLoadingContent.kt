package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataOrange

/** Bottom loading affordance shown while the Official feed requests older pages. */
@Composable
fun OfficialOlderPostsLoadingContent(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        CircularProgressIndicator(
            color = QuataOrange.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 18.dp).size(22.dp),
        )
    }
}
