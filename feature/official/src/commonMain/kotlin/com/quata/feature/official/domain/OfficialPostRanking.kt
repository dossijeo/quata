package com.quata.feature.official.domain

data class OfficialPostRankingInfo(val position: Int, val likes: Int)

fun calculateOfficialPostRanking(posts: List<OfficialPostItem>): Map<String, OfficialPostRankingInfo> =
    posts.sortedWith(compareByDescending<OfficialPostItem> { it.likesCount }.thenByDescending { it.createdAt })
        .mapIndexed { index, post -> post.id to OfficialPostRankingInfo(index + 1, post.likesCount) }
        .toMap()
