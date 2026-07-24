package com.quata.feature.official.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.quata.core.ui.components.QuataFeedActionRail
import com.quata.feature.official.domain.OfficialPostItem

/** Labels remain platform-localized while the Official action-rail structure is shared. */
data class OfficialPostActionRailStrings(
    val like: String,
    val comments: String,
    val share: String,
    val rank: String,
    val live: String,
    val publish: String,
    val delete: String,
)

@Composable
fun OfficialPostActionRailContent(
    post: OfficialPostItem,
    rank: Int,
    isLandscape: Boolean,
    canPublish: Boolean,
    canModerate: Boolean,
    strings: OfficialPostActionRailStrings,
    onCreate: () -> Unit,
    onOpenLive: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataFeedActionRail(
        likes = post.likesCount,
        isLiked = post.isLikedByCurrentUser,
        comments = post.commentsCount,
        postRank = rank,
        isLandscape = isLandscape,
        likeLabel = strings.like,
        commentsLabel = strings.comments,
        shareLabel = strings.share,
        rankLabel = strings.rank,
        liveLabel = strings.live,
        publishLabel = strings.publish,
        deleteLabel = strings.delete,
        showReport = false,
        showDelete = canModerate,
        showPublish = canPublish,
        onLike = onLike,
        onOpenComments = onComment,
        onShare = onShare,
        onOpenLive = onOpenLive,
        onDelete = onDelete,
        onPublish = onCreate,
        modifier = modifier,
    )
}
