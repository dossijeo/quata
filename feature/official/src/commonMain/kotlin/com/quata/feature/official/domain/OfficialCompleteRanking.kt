package com.quata.feature.official.domain

/**
 * Reads a fresh, complete Official snapshot through the existing total-order page contract.
 *
 * Ranking cannot be derived from the first viewport page: omitted rows can change every visible
 * position. The loader therefore fails closed on duplicate rows, oversized pages or a cursor that
 * does not advance instead of presenting a partial list as complete.
 */
suspend fun OfficialRepository.loadCompleteOfficialRanking(
    pageSize: Int = OfficialRankingPageSize,
): Result<List<OfficialPostItem>> = runCatching {
    require(pageSize > 0) { "official_ranking_page_size_invalid" }

    val posts = mutableListOf<OfficialPostItem>()
    val seenIds = mutableSetOf<String>()
    var previousCursor: OfficialFeedCursor? = null
    var page = refreshOfficialFeed().getOrThrow()

    while (true) {
        check(page.size <= pageSize) { "official_ranking_page_oversized" }
        page.forEach { post ->
            check(post.id.isNotBlank() && seenIds.add(post.id)) { "official_ranking_duplicate_post" }
            posts += post
        }
        if (page.size < pageSize) break

        val cursor = page.last().feedCursor()
        check(cursor.sortAt.isNotBlank() && cursor.createdAt.isNotBlank() && cursor.postId.isNotBlank()) {
            "official_ranking_cursor_invalid"
        }
        check(cursor != previousCursor) { "official_ranking_cursor_not_advanced" }
        previousCursor = cursor
        page = loadOlderOfficialFeedPage(cursor = cursor, limit = pageSize).getOrThrow()
    }

    posts
}

private const val OfficialRankingPageSize = 50
