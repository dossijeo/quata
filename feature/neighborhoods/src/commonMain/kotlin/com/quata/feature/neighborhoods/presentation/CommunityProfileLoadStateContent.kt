package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

const val PublicProfileLoadRootTestTag = "public-profile.load"
const val PublicProfileLoadProgressTestTag = "public-profile.load.progress"
const val PublicProfileLoadErrorTestTag = "public-profile.load.error"
const val PublicProfileLoadRetryTestTag = "public-profile.load.retry"
const val PublicProfileLoadBackTestTag = "public-profile.load.back"

/** Portable loading/error state for global profile routes before the full sheet can be rendered. */
@Composable
fun CommunityProfileLoadStateContent(
    isLoading: Boolean,
    errorMessage: String?,
    retryLabel: String,
    backLabel: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().semantics { testTag = PublicProfileLoadRootTestTag }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        testTag = PublicProfileLoadProgressTestTag
                        contentDescription = PublicProfileLoadProgressTestTag
                    },
                )
            } else {
                Text(
                    text = errorMessage ?: "Profile unavailable",
                    modifier = Modifier.semantics {
                        testTag = PublicProfileLoadErrorTestTag
                        contentDescription = PublicProfileLoadErrorTestTag
                    },
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.semantics {
                        testTag = PublicProfileLoadRetryTestTag
                        contentDescription = PublicProfileLoadRetryTestTag
                    },
                ) { Text(retryLabel) }
            }
            Button(
                onClick = onBack,
                modifier = Modifier.semantics {
                    testTag = PublicProfileLoadBackTestTag
                    contentDescription = PublicProfileLoadBackTestTag
                },
            ) { Text(backLabel) }
        }
    }
}
