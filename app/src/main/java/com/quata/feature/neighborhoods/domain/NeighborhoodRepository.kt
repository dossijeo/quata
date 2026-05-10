package com.quata.feature.neighborhoods.domain

import kotlinx.coroutines.flow.Flow

interface NeighborhoodRepository {
    fun observeCommunities(): Flow<List<NeighborhoodCommunity>>
    suspend fun openNeighborhoodChat(neighborhood: String): Result<String>
    suspend fun toggleFollowUser(userId: String): Result<Unit>
    suspend fun openPrivateChat(userId: String): Result<String>
    suspend fun getUserProfile(userId: String): Result<CommunityUserProfile>
}
