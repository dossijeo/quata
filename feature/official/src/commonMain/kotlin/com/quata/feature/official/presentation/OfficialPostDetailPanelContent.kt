package com.quata.feature.official.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quata.core.designsystem.theme.QuataResolvedTheme
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.ui.components.QuataFloatingPanelContent
import kotlinx.coroutines.flow.collectLatest

const val OfficialPostDetailPanelTestTag = "official.detail.panel"
const val OfficialPostDetailCloseTestTag = "official.detail.panel.close"
const val OfficialPostDetailArticleTestTag = "official.detail.article"
const val OfficialPostDetailMediaTestTag = "official.detail.media"
const val OfficialPostDetailLinkTestTag = "official.detail.link"
const val OfficialPostDetailProfileTestTag = "official.detail.profile"
const val OfficialPostDetailScrollTestTag = "official.detail.scroll"

internal const val OfficialPostDetailAuthorSectionKey = "author"
internal const val OfficialPostDetailMediaSectionKey = "media"
internal const val OfficialPostDetailArticleSectionKey = "article"
internal const val OfficialPostDetailResourceSectionKey = "resource"
internal const val OfficialPostDetailNavigationSectionKey = "navigation"

data class OfficialPostDetailScrollAnchor(
    val postId: String,
    val sectionKey: String,
    val scrollOffsetPx: Int,
) {
    companion object {
        val Empty = OfficialPostDetailScrollAnchor("", OfficialPostDetailArticleSectionKey, 0)
        val Saver: Saver<OfficialPostDetailScrollAnchor, Any> = listSaver(
            save = { listOf(it.postId, it.sectionKey, it.scrollOffsetPx) },
            restore = {
                OfficialPostDetailScrollAnchor(
                    postId = it[0] as String,
                    sectionKey = it[1] as String,
                    scrollOffsetPx = it[2] as Int,
                )
            },
        )
    }
}

internal fun officialPostDetailSectionKeys(
    hasAuthor: Boolean,
    hasMedia: Boolean,
    hasResource: Boolean,
    hasNavigation: Boolean,
): List<String> = buildList {
    if (hasAuthor) add(OfficialPostDetailAuthorSectionKey)
    if (hasMedia) add(OfficialPostDetailMediaSectionKey)
    add(OfficialPostDetailArticleSectionKey)
    if (hasResource) add(OfficialPostDetailResourceSectionKey)
    if (hasNavigation) add(OfficialPostDetailNavigationSectionKey)
}

internal fun officialPostDetailInitialScrollPosition(
    postId: String,
    sectionKeys: List<String>,
    anchor: OfficialPostDetailScrollAnchor?,
): Pair<Int, Int> {
    val matching = anchor?.takeIf { it.postId == postId }
    val index = matching?.sectionKey?.let(sectionKeys::indexOf)?.takeIf { it >= 0 }
    return if (index != null) index to matching.scrollOffsetPx.coerceAtLeast(0) else 0 to 0
}

/**
 * Shared detail panel shell. Hosts inject avatar/author, media, external resources and
 * navigation actions; HTML rendering and platform link behavior remain platform-owned.
 * The responsive panel hierarchy itself stays portable.
 */
@Composable
fun OfficialPostDetailPanelContent(
    postId: String,
    title: String,
    closeLabel: String,
    link: String?,
    onDismiss: () -> Unit,
    articleContent: @Composable (Modifier) -> Unit,
    author: (@Composable (Modifier) -> Unit)? = null,
    media: (@Composable (Modifier) -> Unit)? = null,
    resourceContent: (@Composable (Modifier) -> Unit)? = null,
    navigationContent: (@Composable (Modifier) -> Unit)? = null,
    initialScrollAnchor: OfficialPostDetailScrollAnchor? = null,
    onScrollAnchorChanged: (OfficialPostDetailScrollAnchor) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val template = quataTheme()
    val sectionKeys = remember(
        author != null,
        media != null,
        resourceContent != null,
        link?.isNotBlank() == true,
        navigationContent != null,
    ) {
        officialPostDetailSectionKeys(
            hasAuthor = author != null,
            hasMedia = media != null,
            hasResource = resourceContent != null || link?.isNotBlank() == true,
            hasNavigation = navigationContent != null,
        )
    }
    val listState = remember(postId) {
        val (index, offset) = officialPostDetailInitialScrollPosition(postId, sectionKeys, initialScrollAnchor)
        LazyListState(index, offset)
    }
    LaunchedEffect(postId, listState, sectionKeys) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest { (index, offset) ->
                sectionKeys.getOrNull(index)?.let { sectionKey ->
                    onScrollAnchorChanged(
                        OfficialPostDetailScrollAnchor(
                            postId = postId,
                            sectionKey = sectionKey,
                            scrollOffsetPx = offset,
                        ),
                    )
                }
            }
    }
    QuataFloatingPanelContent(
        onDismiss = onDismiss,
        modifier = modifier
            .testTag(OfficialPostDetailPanelTestTag)
            .semantics { contentDescription = OfficialPostDetailPanelTestTag },
        template = template,
        landscapeHeightFraction = 0.86f,
        landscapeVerticalOffset = (-24).dp,
    ) { panelModifier, isLandscape ->
        Column(
            modifier = panelModifier.padding(
                start = 18.dp,
                top = if (isLandscape) 18.dp else 10.dp,
                end = 18.dp,
                bottom = if (isLandscape) 18.dp else 48.dp,
            ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .testTag(OfficialPostDetailCloseTestTag)
                        .semantics { contentDescription = OfficialPostDetailCloseTestTag },
                ) { Text(closeLabel) }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).testTag(OfficialPostDetailScrollTestTag),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                author?.let { authorSlot ->
                    item(key = OfficialPostDetailAuthorSectionKey) { authorSlot(Modifier.fillMaxWidth()) }
                }
                media?.let { mediaSlot ->
                    item(key = OfficialPostDetailMediaSectionKey) {
                        mediaSlot(
                            Modifier
                                .fillMaxWidth()
                                .height(224.dp)
                                .testTag(OfficialPostDetailMediaTestTag)
                                .semantics { contentDescription = OfficialPostDetailMediaTestTag },
                        )
                    }
                }
                item(key = OfficialPostDetailArticleSectionKey) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .testTag(OfficialPostDetailArticleTestTag)
                            .semantics { contentDescription = OfficialPostDetailArticleTestTag },
                    ) {
                        articleContent(Modifier.fillMaxWidth())
                    }
                }
                when {
                    resourceContent != null -> item(key = OfficialPostDetailResourceSectionKey) {
                        resourceContent(
                            Modifier
                                .fillMaxWidth()
                                .testTag(OfficialPostDetailLinkTestTag)
                                .semantics { contentDescription = OfficialPostDetailLinkTestTag },
                        )
                    }
                    link?.isNotBlank() == true -> item(key = OfficialPostDetailResourceSectionKey) {
                        Text(
                            link,
                            color = if (template.resolvedTheme == QuataResolvedTheme.Dark) Color(0xFF2EA7FF) else Color(0xFF17954B),
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .testTag(OfficialPostDetailLinkTestTag)
                                .semantics { contentDescription = OfficialPostDetailLinkTestTag },
                        )
                    }
                }
                navigationContent?.let { navigationSlot ->
                    item(key = OfficialPostDetailNavigationSectionKey) {
                        navigationSlot(
                            Modifier
                                .fillMaxWidth()
                                .testTag(OfficialPostDetailProfileTestTag)
                                .semantics { contentDescription = OfficialPostDetailProfileTestTag },
                        )
                    }
                }
            }
        }
    }
}
