package com.quata.feature.official.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.feature.official.domain.OfficialPostItem

/**
 * Portable shell for an Official post. Platform hosts provide media, avatar, navigation and
 * action implementations, while the responsive card hierarchy remains shared.
 */
@Composable
fun OfficialPostCardContent(
    post: OfficialPostItem,
    typeLabel: String,
    readMoreLabel: String,
    isLandscape: Boolean,
    author: @Composable (Modifier) -> Unit,
    media: (@Composable (Modifier) -> Unit)?,
    actionRail: @Composable (isLandscape: Boolean, Modifier) -> Unit,
    overflowAction: (@Composable (Modifier) -> Unit)? = null,
    onReadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    val hasMedia = media != null
    Card(
        colors = CardDefaults.cardColors(containerColor = template.colors.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, template.colors.divider.copy(alpha = 0.7f), RoundedCornerShape(20.dp)),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        template.colors.surface.copy(alpha = 0.98f),
                        template.colors.surfaceRaised.copy(alpha = 0.78f),
                    ),
                ),
            ),
        ) {
            if (isLandscape) {
                OfficialPostLandscapeBody(
                    post = post,
                    readMoreLabel = readMoreLabel,
                    hasMedia = hasMedia,
                    author = author,
                    media = media,
                    onReadMore = onReadMore,
                    modifier = Modifier.fillMaxSize().padding(start = 18.dp, top = 18.dp, end = 76.dp, bottom = 18.dp),
                )
                actionRail(true, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 16.dp))
                OfficialTypePill(typeLabel, Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 14.dp))
                overflowAction?.invoke(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 28.dp))
            } else {
                OfficialPostPortraitBody(
                    post = post,
                    readMoreLabel = readMoreLabel,
                    hasMedia = hasMedia,
                    author = author,
                    media = media,
                    onReadMore = onReadMore,
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp, top = 20.dp, end = 76.dp, bottom = 18.dp),
                )
                actionRail(false, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 16.dp))
                OfficialTypePill(typeLabel, Modifier.align(Alignment.TopEnd).padding(top = 38.dp, end = 8.dp))
            }
        }
    }
}

@Composable
private fun OfficialPostPortraitBody(
    post: OfficialPostItem, readMoreLabel: String, hasMedia: Boolean,
    author: @Composable (Modifier) -> Unit, media: (@Composable (Modifier) -> Unit)?,
    onReadMore: () -> Unit, modifier: Modifier,
) {
    Column(modifier) {
        author(Modifier)
        Spacer(Modifier.height(16.dp))
        if (!hasMedia) {
            OfficialPostTextOnlyShared(post, readMoreLabel, onReadMore)
        } else {
            media?.invoke(Modifier.fillMaxWidth().weight(1f, fill = true))
            Spacer(Modifier.height(12.dp))
            OfficialPostTextBlock(post, titleSize = 17, compact = true)
            OfficialReadMoreLink(post, readMoreLabel, onReadMore)
        }
    }
}

@Composable
private fun OfficialPostLandscapeBody(
    post: OfficialPostItem, readMoreLabel: String, hasMedia: Boolean,
    author: @Composable (Modifier) -> Unit, media: (@Composable (Modifier) -> Unit)?,
    onReadMore: () -> Unit, modifier: Modifier,
) {
    if (!hasMedia) {
        Column(modifier) {
            author(Modifier.padding(end = 96.dp))
            Spacer(Modifier.height(16.dp))
            OfficialPostTextOnlyShared(post, readMoreLabel, onReadMore, alignReadMoreEnd = true)
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            media?.invoke(Modifier.weight(1.08f).fillMaxHeight())
            Column(Modifier.weight(0.92f).fillMaxHeight()) {
                author(Modifier.padding(end = 96.dp))
                Spacer(Modifier.height(18.dp))
                OfficialPostTextBlock(post, titleSize = 22, modifier = Modifier.weight(1f, fill = false))
                OfficialReadMoreLink(post, readMoreLabel, onReadMore)
            }
        }
    }
}

@Composable
private fun OfficialPostTextOnlyShared(
    post: OfficialPostItem, readMoreLabel: String, onReadMore: () -> Unit,
    alignReadMoreEnd: Boolean = false, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                OfficialPostTextBlock(post, titleSize = 26)
            }
        }
        OfficialReadMoreLink(post, readMoreLabel, onReadMore, alignEnd = alignReadMoreEnd)
    }
}
