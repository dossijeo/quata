package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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

/** Visible recovery affordance that keeps the already loaded Official posts on screen. */
@Composable
fun OfficialOlderPostsFailureContent(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag(OfficialOlderPostsErrorTestTag),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 6.dp,
    ) {
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag(OfficialOlderPostsRetryTestTag),
        ) {
            Text("$message · $retryLabel")
        }
    }
}

const val OfficialOlderPostsErrorTestTag = "official-older-posts-error"
const val OfficialOlderPostsRetryTestTag = "official-older-posts-retry"
