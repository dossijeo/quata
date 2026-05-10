package com.quata.feature.neighborhoods.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.quata.core.ui.components.QuataScreen

@Composable
fun NeighborhoodsScreen(padding: PaddingValues) {
    QuataScreen(padding) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Barrios", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
