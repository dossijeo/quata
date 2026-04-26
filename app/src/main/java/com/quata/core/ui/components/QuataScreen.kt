package com.quata.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.designsystem.theme.QuataBackground

@Composable
fun QuataScreen(
    padding: PaddingValues = PaddingValues(),
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(QuataBackground)
            .padding(padding)
    ) {
        content()
    }
}
