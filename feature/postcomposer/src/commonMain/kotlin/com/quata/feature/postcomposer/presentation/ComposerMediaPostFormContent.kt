package com.quata.feature.postcomposer.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared media-post flow: acquisition first, portable controls second, then preview and publish.
 *
 * Camera/gallery launchers, URI labels, bitmap/video rendering and editors stay in the platform
 * slots. Keeping the sequence here lets Android and browser hosts use the same responsive form
 * without making common presentation depend on any media API.
 */
@Composable
fun ComposerMediaPostFormContent(
    isLandscapeLayout: Boolean,
    mediaSource: @Composable ColumnScope.() -> Unit,
    preview: @Composable ColumnScope.() -> Unit,
    publish: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    controls: (@Composable ColumnScope.() -> Unit)? = null,
) {
    ComposerMediaPostFormLayoutContent(
        isLandscapeLayout = isLandscapeLayout,
        controls = {
            mediaSource()
            controls?.let { additionalControls ->
                additionalControls()
            }
        },
        preview = preview,
        publish = publish,
        modifier = modifier,
    )
}
