package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared structural section for Official editor media. Platform launchers, decoders and media
 * editors stay injected so this layout can be reused without importing Android APIs.
 */
@Composable
fun OfficialEditorMediaSectionContent(
    title: String,
    imagePicker: @Composable (Modifier) -> Unit,
    videoPicker: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    preview: (@Composable ColumnScope.() -> Unit)? = null,
) {
    OfficialEditorSectionCardContent(modifier = modifier) {
        OfficialEditorSectionTitleContent(title)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            imagePicker(Modifier.weight(1f))
            videoPicker(Modifier.weight(1f))
        }
        preview?.invoke(this)
    }
}
