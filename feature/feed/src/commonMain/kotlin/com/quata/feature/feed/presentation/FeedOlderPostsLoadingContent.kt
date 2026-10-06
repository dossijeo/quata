package com.quata.feature.feed.presentation

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

@Composable
fun FeedOlderPostsLoadingContent(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        CircularProgressIndicator(
            color = QuataOrange.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 18.dp).size(22.dp),
        )
    }
}

@Composable
fun FeedOlderPostsFailureContent(
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag(FeedOlderPostsErrorTestTag),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 6.dp,
    ) {
        TextButton(
            onClick = onRetry,
            modifier = Modifier.testTag(FeedOlderPostsRetryTestTag),
        ) {
            Text("$message · $retryLabel")
        }
    }
}

const val FeedOlderPostsErrorTestTag = "feed-older-posts-error"
const val FeedOlderPostsRetryTestTag = "feed-older-posts-retry"
