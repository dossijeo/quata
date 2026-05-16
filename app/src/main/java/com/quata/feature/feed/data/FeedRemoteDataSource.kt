package com.quata.feature.feed.data

import com.quata.data.supabase.CommunityComment
import com.quata.data.supabase.CommunityPost
import com.quata.data.supabase.CommunityPostLike
import com.quata.data.supabase.CommunityProfile
import com.quata.data.supabase.SupabaseCommunityApi

class FeedRemoteDataSource(
    private val supabaseApi: SupabaseCommunityApi
) {
    suspend fun getPosts(): List<CommunityPost> = supabaseApi.getFeedPosts(limit = 50)

    suspend fun getComments(postIds: Collection<String>): List<CommunityComment> =
        supabaseApi.getComments(postIds)

    suspend fun getLikes(postIds: Collection<String>): List<CommunityPostLike> =
        supabaseApi.getLikes(postIds)

    suspend fun getProfiles(profileIds: Collection<String>): List<CommunityProfile> =
        supabaseApi.getProfiles(profileIds)

    suspend fun toggleLike(postId: String, profileId: String) =
        supabaseApi.toggleLike(postId, profileId)

    suspend fun addComment(postId: String, profileId: String, body: String) =
        supabaseApi.addComment(postId, profileId, body)
}
