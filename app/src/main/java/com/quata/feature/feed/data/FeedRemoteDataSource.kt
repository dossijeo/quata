package com.quata.feature.feed.data

import com.quata.data.supabase.CommunityComment
import com.quata.data.supabase.CommunityPost
import com.quata.data.supabase.CommunityPostLike
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.SupabaseCommunityApi
import kotlinx.coroutines.flow.Flow

class FeedRemoteDataSource(
    private val supabaseApi: SupabaseCommunityApi
) {
    suspend fun getPosts(): List<CommunityPost> = supabaseApi.getFeedPosts(limit = 50)

    fun observePosts(): Flow<List<CommunityPost>> = supabaseApi.observeFeedPosts(limit = 50)

    suspend fun getPost(postId: String): CommunityPost? =
        supabaseApi.getFeedPosts(limit = 1, postId = postId).firstOrNull()

    fun observePost(postId: String): Flow<List<CommunityPost>> =
        supabaseApi.observeFeedPosts(limit = 1, postId = postId)

    suspend fun getComments(postIds: Collection<String>): List<CommunityComment> =
        supabaseApi.getComments(postIds)

    fun observeComments(postIds: Collection<String>): Flow<List<CommunityComment>> =
        supabaseApi.observeComments(postIds)

    suspend fun getLikes(postIds: Collection<String>): List<CommunityPostLike> =
        supabaseApi.getLikes(postIds)

    fun observeLikes(postIds: Collection<String>): Flow<List<CommunityPostLike>> =
        supabaseApi.observeLikes(postIds)

    suspend fun getProfiles(profileIds: Collection<String>): List<CommunityProfile> =
        supabaseApi.getProfiles(profileIds)

    fun observeProfiles(profileIds: Collection<String>): Flow<List<CommunityProfile>> =
        supabaseApi.observeProfiles(profileIds)

    suspend fun toggleLike(postId: String, profileId: String) =
        supabaseApi.toggleLike(postId, profileId)

    suspend fun addComment(postId: String, profileId: String, body: String) =
        supabaseApi.addComment(postId, profileId, body)

    suspend fun deletePost(postId: String, profileId: String) =
        supabaseApi.deletePost(postId, profileId)
}
