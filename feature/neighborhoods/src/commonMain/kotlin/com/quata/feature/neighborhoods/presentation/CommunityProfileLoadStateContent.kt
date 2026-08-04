package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Portable loading/error state for global profile routes before the full sheet can be rendered. */
@Composable
fun CommunityProfileLoadStateContent(
    isLoading: Boolean,
    errorMessage: String?,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isLoading) CircularProgressIndicator()
        else Text(errorMessage ?: "Profile unavailable")
        Button(onClick = onBack) { Text(backLabel) }
    }
}
