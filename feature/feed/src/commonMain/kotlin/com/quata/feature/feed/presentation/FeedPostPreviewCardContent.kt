package com.quata.feature.feed.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quata.core.ui.components.QuataCard

/**
 * Portable post-card/preview hierarchy for lists and detail launchers.
 *
 * Media decoding, avatars, navigation and translation engines remain host-owned slots. The
 * common layer only fixes their relative structure so Android, Web and iOS do not duplicate the
 * visual card shell while retaining platform-specific media/action implementations.
 */
@Composable
fun FeedPostPreviewCardContent(
    author: @Composable (Modifier) -> Unit,
    media: @Composable BoxScope.() -> Unit,
    actionRail: @Composable BoxScope.() -> Unit,
    navigation: @Composable BoxScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    translation: (@Composable ColumnScope.() -> Unit)? = null,
    mediaAspectRatio: Float = 16f / 10f,
) {
    QuataCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(mediaAspectRatio)
                    .background(Color.Black),
            ) {
                media()
                navigation()
                actionRail()
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                author(Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                body()
                translation?.let { content ->
                    Spacer(Modifier.height(10.dp))
                    content()
                }
            }
        }
    }
}
