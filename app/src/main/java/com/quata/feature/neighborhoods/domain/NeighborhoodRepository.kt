package com.quata.feature.neighborhoods.domain

import kotlinx.coroutines.flow.Flow

interface NeighborhoodRepository {
    fun observeCommunities(): Flow<List<NeighborhoodCommunity>>
    suspend fun openNeighborhoodChat(neighborhood: String): Result<String>
    suspend fun toggleFollowUser(userId: String): Result<FollowUserResult>
    suspend fun reportPost(postId: String): Result<Unit>
    suspend fun openPrivateChat(userId: String): Result<String>
    suspend fun isCurrentUserAdmin(): Boolean
    suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean): Result<NeighborhoodUser>
    suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long? = null): CommunityUserProfile?
    suspend fun cacheUserProfile(profile: CommunityUserProfile)
    fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>>
    suspend fun getUserProfile(userId: String): Result<CommunityUserProfile>
}
