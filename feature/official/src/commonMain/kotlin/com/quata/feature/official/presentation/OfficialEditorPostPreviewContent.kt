package com.quata.feature.official.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.quata.feature.official.domain.OfficialPostItem

/**
 * Shared editor preview flow: portrait post card plus its read-more panel. Media decoding,
 * avatar rendering and rich-text rendering remain platform slots. Product actions are not shown
 * here unless a host injects working callbacks.
 */
@Composable
fun OfficialEditorPostPreviewContent(
    post: OfficialPostItem,
    typeLabel: String,
    readMoreLabel: String,
    closeLabel: String,
    author: @Composable (Modifier) -> Unit,
    media: (@Composable (Modifier) -> Unit)?,
    actionRail: (@Composable (isLandscape: Boolean, Modifier) -> Unit)? = null,
    articleContent: @Composable (OfficialPostItem, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    overflowAction: (@Composable (Modifier) -> Unit)? = null,
) {
    var readMorePost by remember(post.id, post.contentHtml, post.contentPlain) { mutableStateOf<OfficialPostItem?>(null) }
    OfficialPostPreviewFrameContent(modifier = modifier) { cardModifier ->
        OfficialPostCardContent(
            post = post,
            typeLabel = typeLabel,
            readMoreLabel = readMoreLabel,
            isLandscape = false,
            author = author,
            media = media,
            actionRail = actionRail,
            overflowAction = overflowAction,
            onReadMore = { readMorePost = post },
            modifier = cardModifier,
        )
    }
    readMorePost?.let { selectedPost ->
        OfficialPostDetailPanelContent(
            title = readMoreLabel,
            closeLabel = closeLabel,
            link = selectedPost.linkUrl,
            onDismiss = { readMorePost = null },
            articleContent = { articleModifier -> articleContent(selectedPost, articleModifier) },
        )
    }
}
