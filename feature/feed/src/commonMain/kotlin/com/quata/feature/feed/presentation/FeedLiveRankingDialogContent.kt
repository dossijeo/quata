package com.quata.feature.feed.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.quata.core.model.Post
import com.quata.core.ui.components.QuataLiveRankingItem

/**
 * Shared Feed live-ranking dialog model and interaction wiring.
 *
 * The platform owns the floating-panel host, localized post-type labels and avatar rendering;
 * Feed keeps the ranking order, item projection and post-id navigation mapping portable.
 */
@Composable
fun FeedLiveRankingDialogContent(
    posts: List<Post>,
    rankForPost: (Post) -> Int,
    postTypeLabel: @Composable (Post) -> String,
    panel: @Composable (
        items: List<QuataLiveRankingItem>,
        onDismiss: () -> Unit,
        onOpenItem: (String) -> Unit,
    ) -> Unit,
    onDismiss: () -> Unit,
    onOpenPost: (Post) -> Unit,
) {
    val rankedPosts = remember(posts) { posts.sortedBy(rankForPost) }
    val postsById = remember(posts) { posts.associateBy { it.id } }
    val items = rankedPosts.map { post ->
        QuataLiveRankingItem(
            id = post.id,
            profileId = post.author.id,
            rank = rankForPost(post),
            title = post.author.displayName,
            subtitle = postTypeLabel(post),
            avatarName = post.author.displayName,
            avatarUrl = post.author.avatarUrl,
            isOfficial = post.author.isOfficial,
            likesCount = post.likesCount,
        )
    }
    panel(items, onDismiss) { postId -> postsById[postId]?.let(onOpenPost) }
}
