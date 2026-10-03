package com.quata.feature.neighborhoods.domain

import com.quata.core.model.Post
import com.quata.core.model.PostComment
import kotlinx.coroutines.flow.Flow

interface NeighborhoodRepository {
    fun observeCommunities(): Flow<List<NeighborhoodCommunity>>
    suspend fun openNeighborhoodChat(neighborhood: String): Result<String>
    suspend fun toggleFollowUser(userId: String): Result<FollowUserResult>
    suspend fun toggleProfilePostLike(postId: String): Result<Post?>
    suspend fun addProfileComment(postId: String, comment: PostComment): Result<Post?>
    suspend fun reportPost(postId: String): Result<Unit>
    suspend fun reportProfile(userId: String): Result<Unit>
    suspend fun setProfileBlocked(userId: String, blocked: Boolean): Result<Boolean>
    suspend fun openPrivateChat(userId: String): Result<String>
    suspend fun isCurrentUserAdmin(): Boolean
    suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean): Result<NeighborhoodUser>
    suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long? = null): CommunityUserProfile?
    suspend fun cacheUserProfile(profile: CommunityUserProfile)
    fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>>
    suspend fun getUserProfile(userId: String): Result<CommunityUserProfile>
}

/** A public directory read was rejected by the backend authorization boundary. */
class NeighborhoodDirectoryAccessDeniedException(cause: Throwable? = null) :
    IllegalStateException("neighborhood_directory_access_denied", cause)

/** Preserves all non-authorization failures while normalizing 401/403 across platform transports. */
fun neighborhoodDirectoryFailure(statusCode: Int?, cause: Throwable): Throwable =
    if (statusCode == 401 || statusCode == 403) NeighborhoodDirectoryAccessDeniedException(cause) else cause
