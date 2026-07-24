package com.quata.feature.feed.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.quata.core.model.Post
import com.quata.core.text.extractPostMeta
import com.quata.core.text.parsePostShortcodeContent
import com.quata.core.ui.components.QuataFeedActionRail
import com.quata.core.ui.components.QuataFeedOverflowActionButton

/** Localized labels and platform-independent formatting used by the shared reel structure. */
data class FeedReelStrings(
    val like: String,
    val comments: String,
    val share: String,
    val rank: String,
    val live: String,
    val publish: String,
    val report: String,
    val delete: String,
    val locationLabel: @Composable (String) -> String,
)

/**
 * Structural Feed reel shared by all Compose hosts.
 *
 * Video/image decoding and avatar rendering are explicit slots. Navigation, share and mutation
 * policy is delegated to callbacks, while the common layer owns shortcode parsing, description
 * expansion, overlay placement and the complete action hierarchy.
 */
@Composable
fun FeedReelPostContent(
    post: Post,
    postRank: Int,
    isLandscape: Boolean,
    canDelete: Boolean,
    strings: FeedReelStrings,
    media: @Composable BoxScope.() -> Unit,
    avatar: @Composable () -> Unit,
    onOpenComments: () -> Unit,
    onOpenLive: () -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onCreatePost: () -> Unit,
) {
    val isVideo = post.videoUrl != null
    val shortcodeContent = remember(post.text) { post.text.parsePostShortcodeContent() }
    val postMeta = remember(post.text) { post.text.extractPostMeta() }
    val displayText = shortcodeContent.cleanText
    val isTextOnly = post.videoUrl == null && post.imageUrl == null && displayText.isNotBlank()
    var isDescriptionExpanded by rememberSaveable(post.id) { mutableStateOf(false) }
    val mediaBadgeText = when {
        post.videoUrl != null -> postMeta.mediaTitle.ifBlank { postMeta.imageLocation }
        post.imageUrl != null -> postMeta.imageLocation
        else -> ""
    }
    val hasTopOverlayText = mediaBadgeText.isNotBlank() || !shortcodeContent.documentText.isNullOrBlank()

    ReelPostOverlayContent(
        isVideo = isVideo,
        isLandscape = isLandscape,
        media = media,
        topOverlay = {
            ReelTopOverlayContent(
                showTopScrim = !isVideo || hasTopOverlayText,
                documentText = shortcodeContent.documentText,
                mediaBadgeText = mediaBadgeText,
                isVideo = isVideo,
                locationLabel = strings.locationLabel,
            )
        },
        actionRail = {
            QuataFeedActionRail(
                likes = post.likesCount,
                isLiked = post.isLikedByCurrentUser,
                comments = post.comments.size,
                postRank = postRank,
                isLandscape = isLandscape,
                likeLabel = strings.like,
                commentsLabel = strings.comments,
                shareLabel = strings.share,
                rankLabel = strings.rank,
                liveLabel = strings.live,
                publishLabel = strings.publish,
                isReported = post.isReportedByCurrentUser,
                reportLabel = strings.report,
                deleteLabel = strings.delete,
                showReport = true,
                showDelete = canDelete,
                showPublish = true,
                onLike = onLike,
                onOpenComments = onOpenComments,
                onOpenLive = onOpenLive,
                onShare = onShare,
                onReport = onReport,
                onDelete = onDelete,
                onPublish = onCreatePost,
            )
        },
        overflowAction = {
            QuataFeedOverflowActionButton(
                postRank = postRank,
                rankLabel = strings.rank,
                liveLabel = strings.live,
                reportLabel = strings.report,
                showReport = true,
                onOpenLive = onOpenLive,
                onReport = onReport,
            )
        },
        author = {
            ReelAuthorContent(
                displayName = post.author.displayName,
                neighborhood = post.author.neighborhood,
                displayText = displayText,
                showDescription = (isVideo || post.imageUrl != null) && displayText.isNotBlank() && !isTextOnly,
                isDescriptionExpanded = isDescriptionExpanded,
                onToggleDescription = { isDescriptionExpanded = !isDescriptionExpanded },
                avatar = avatar,
            )
        },
    )
}
