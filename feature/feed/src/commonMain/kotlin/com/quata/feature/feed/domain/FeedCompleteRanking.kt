package com.quata.feature.feed.domain

import com.quata.core.model.Post

/** Read a fresh complete ranking snapshot without mutating the visible feed pager. */
suspend fun FeedRepository.loadCompleteFeedRanking(
    pageSize: Int = FeedRankingPageSize,
): Result<List<Post>> = runCatching {
    require(pageSize in 1..100) { "feed_ranking_page_size_invalid" }
    val result = mutableListOf<Post>()
    val seenIds = mutableSetOf<String>()
    var page = refreshFeed().getOrThrow()
    var previousCursor: FeedCursor? = null

    while (true) {
        check(page.size <= pageSize) { "feed_ranking_page_oversized" }
        page.forEach { post ->
            check(post.id.isNotBlank() && seenIds.add(post.id)) { "feed_ranking_duplicate_post" }
        }
        result += page
        if (page.size < pageSize) break

        val last = page.last()
        val cursor = FeedCursor(last.createdAt, last.id)
        check(cursor.createdAt.isNotBlank() && cursor.postId.isNotBlank()) {
            "feed_ranking_cursor_invalid"
        }
        check(cursor != previousCursor) { "feed_ranking_cursor_not_advanced" }
        previousCursor = cursor
        page = loadOlderFeedPage(cursor, pageSize).getOrThrow()
    }
    result
}

private const val FeedRankingPageSize = 50
