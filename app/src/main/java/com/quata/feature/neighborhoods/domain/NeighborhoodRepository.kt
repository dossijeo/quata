package com.quata.feature.neighborhoods.domain

import kotlinx.coroutines.flow.Flow

interface NeighborhoodRepository {
    fun observeCommunities(): Flow<List<NeighborhoodCommunity>>
    suspend fun openNeighborhoodChat(neighborhood: String): Result<String>
}
