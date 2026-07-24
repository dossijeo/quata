package com.quata.feature.official.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared structure for the post body section of the Official editor.
 *
 * Hosts keep control of localized strings, rich-text editors and platform actions, while the
 * section hierarchy stays available to every Compose target.
 */
@Composable
fun OfficialEditorBodySectionContent(
    title: String,
    readMoreControl: (@Composable () -> Unit)? = null,
    editorAction: @Composable () -> Unit,
    linkControl: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    OfficialEditorSectionCardContent(modifier = modifier) {
        OfficialEditorSectionTitleContent(title)
        readMoreControl?.invoke()
        editorAction()
        linkControl?.invoke()
    }
}
