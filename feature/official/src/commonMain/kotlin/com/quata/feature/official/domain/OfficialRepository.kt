package com.quata.feature.official.domain

import com.quata.core.model.PostComment
import com.quata.core.model.User
import kotlinx.coroutines.flow.Flow

interface OfficialRepository {
    fun observeOfficialFeed(): Flow<Result<List<OfficialPostItem>>>
    suspend fun getOfficialFeed(): Result<List<OfficialPostItem>>
    suspend fun refreshOfficialFeed(): Result<List<OfficialPostItem>>
    suspend fun loadOlderOfficialFeedPage(beforePublishedAt: String?, limit: Int): Result<List<OfficialPostItem>>
    suspend fun getOfficialPost(postId: String): Result<OfficialPostItem?>
    suspend fun refreshCurrentUser(): Result<User?>
    suspend fun createPost(draft: OfficialPostDraft): Result<OfficialPostItem?>
    suspend fun createPosts(drafts: List<OfficialPostDraft>): Result<OfficialPostItem?> =
        drafts.firstOrNull()?.let { createPost(it) } ?: Result.success(null)
    suspend fun deletePost(postId: String): Result<Unit>
    suspend fun toggleLike(postId: String): Result<OfficialPostItem?>
    suspend fun addComment(postId: String, comment: PostComment): Result<OfficialPostItem?>
}
