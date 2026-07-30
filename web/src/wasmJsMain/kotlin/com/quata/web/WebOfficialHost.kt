package com.quata.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.model.Post
import com.quata.core.platform.ShareService
import com.quata.core.ui.richtext.QuataRichTextRenderer
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.presentation.OfficialFeedScreenHost
import com.quata.feature.official.presentation.OfficialFeedScreenPlatformSlots
import com.quata.feature.official.presentation.OfficialPostMediaFrameContent

/** Browser adapter: navigation and browser services only; the Official screen itself is common. */
@Composable
fun WebOfficialHost(
    repository: WebOfficialRepository,
    shareService: ShareService,
    officialPostId: String?,
    currentUserId: String?,
    onAuthRequired: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onCreateOfficialPost: () -> Unit,
    modifier: Modifier = Modifier,
) = OfficialFeedScreenHost(
    padding = PaddingValues(),
    repository = repository,
    currentUserId = currentUserId,
    focusedPostId = officialPostId,
    onAuthRequired = onAuthRequired,
    onOpenUserProfile = onOpenUserProfile,
    onCreateOfficialPost = onCreateOfficialPost,
    modifier = modifier,
    slots = OfficialFeedScreenPlatformSlots(
        avatar = { post, avatarModifier -> BrowserFeedAuthorAvatar(post.asFeedPost(), onOpenUserProfile) },
        media = { post, mediaModifier, open ->
            OfficialPostMediaFrameContent(
                onOpenMedia = open,
                showPlayButton = post.mediaType == com.quata.feature.official.domain.OfficialMediaType.Video,
                modifier = mediaModifier,
                media = { surface -> Box(surface) { BrowserFeedMediaContent(post.asFeedPost(), false, true, 0L, {}, {}) } },
            )
        },
        article = { post, articleModifier -> QuataRichTextRenderer(post.contentHtml, articleModifier, post.contentPlain) },
        mediaViewer = { post, dismiss -> post.mediaUrl?.let { url -> openBrowserUrl(url) }; dismiss() },
        share = { payload -> shareService.share(payload) },
        showComposeMessage = true,
        openUrl = { url -> openBrowserUrl(url) },
        rankingAvatar = { item -> BrowserFeedRankingAvatar(item) },
    ),
)

private fun OfficialPostItem.asFeedPost() = Post(
    id = id,
    author = author,
    text = contentPlain.ifBlank { summary },
    imageUrl = mediaUrl?.takeIf { mediaType != com.quata.feature.official.domain.OfficialMediaType.Video },
    videoUrl = mediaUrl?.takeIf { mediaType == com.quata.feature.official.domain.OfficialMediaType.Video },
    createdAt = createdAt,
)

private fun openBrowserUrl(url: String): Unit = js("globalThis.open(url, '_blank', 'noopener,noreferrer')")
