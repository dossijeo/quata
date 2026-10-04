package com.quata.feature.official.presentation

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

const val OfficialRankingLoadingTestTag = "official-ranking-loading"
const val OfficialRankingErrorTestTag = "official-ranking-error"
const val OfficialRankingRetryTestTag = "official-ranking-retry"

@Composable
internal fun OfficialRankingLoadStateContent(
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
            modifier.fillMaxSize().testTag(OfficialRankingLoadingTestTag),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        error != null -> Column(
            modifier.fillMaxSize().testTag(OfficialRankingErrorTestTag),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(errorMessage)
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(OfficialRankingRetryTestTag),
            ) { Text(retryLabel) }
        }
        else -> content(modifier)
    }
}
