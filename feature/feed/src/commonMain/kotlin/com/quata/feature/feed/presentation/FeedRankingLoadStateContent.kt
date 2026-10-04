package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val FeedRankingLoadingTestTag = "feed-ranking-loading"
const val FeedRankingErrorTestTag = "feed-ranking-error"
const val FeedRankingRetryTestTag = "feed-ranking-retry"

@Composable
internal fun FeedRankingLoadStateContent(
    isLoading: Boolean,
    error: String?,
    errorMessage: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    when {
        isLoading -> Box(
            modifier.fillMaxSize().testTag(FeedRankingLoadingTestTag),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        error != null -> Column(
            modifier.fillMaxSize().testTag(FeedRankingErrorTestTag),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(errorMessage)
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(FeedRankingRetryTestTag),
            ) { Text(retryLabel) }
        }
        else -> content(modifier)
    }
}
