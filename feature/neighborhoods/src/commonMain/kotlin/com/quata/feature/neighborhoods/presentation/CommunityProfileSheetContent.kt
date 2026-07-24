package com.quata.feature.neighborhoods.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared modal shell for a Community profile. Hosts own navigation, attachment viewers and the
 * profile regions rendered inside this sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityProfileSheetContent(
    sheetState: SheetState,
    containerColor: Color,
    contentColor: Color,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content,
    )
}
