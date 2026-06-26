package com.quata.feature.feed.data

import com.quata.data.supabase.CommunityComment
import com.quata.data.supabase.CommunityPost
import com.quata.data.supabase.CommunityPostLike
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.SupabaseCacheMode
import com.quata.data.supabase.SupabaseCommunityApi
import kotlinx.coroutines.flow.Flow

class FeedRemoteDataSource(
    private val supabaseApi: SupabaseCommunityApi
) {
    suspend fun getPosts(cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST): List<CommunityPost> =
        supabaseApi.getFeedPosts(limit = 50, cacheMode = cacheMode)

    fun observePosts(): Flow<List<CommunityPost>> = supabaseApi.observeFeedPosts(limit = 50)

    suspend fun getPost(
        postId: String,
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): CommunityPost? =
        supabaseApi.getFeedPosts(limit = 1, postId = postId, cacheMode = cacheMode).firstOrNull()

    fun observePost(postId: String): Flow<List<CommunityPost>> =
        supabaseApi.observeFeedPosts(limit = 1, postId = postId)

    suspend fun getComments(
        postIds: Collection<String>,
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): List<CommunityComment> =
        supabaseApi.getComments(postIds, cacheMode = cacheMode)

    fun observeComments(postIds: Collection<String>): Flow<List<CommunityComment>> =
        supabaseApi.observeComments(postIds)

    suspend fun getLikes(
        postIds: Collection<String>,
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): List<CommunityPostLike> =
        supabaseApi.getLikes(postIds, cacheMode = cacheMode)

    fun observeLikes(postIds: Collection<String>): Flow<List<CommunityPostLike>> =
        supabaseApi.observeLikes(postIds)

    suspend fun getProfiles(
        profileIds: Collection<String>,
        cacheMode: SupabaseCacheMode = SupabaseCacheMode.CACHE_FIRST
    ): List<CommunityProfile> =
        supabaseApi.getProfiles(profileIds, cacheMode = cacheMode)

    fun observeProfiles(profileIds: Collection<String>): Flow<List<CommunityProfile>> =
        supabaseApi.observeProfiles(profileIds)

    suspend fun toggleLike(postId: String, profileId: String) =
        supabaseApi.toggleLike(postId, profileId)

    suspend fun addComment(postId: String, profileId: String, body: String) =
        supabaseApi.addComment(postId, profileId, body)

    suspend fun deletePost(postId: String, profileId: String) =
        supabaseApi.deletePost(postId, profileId)
}
