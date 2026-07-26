@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.CommunityMutationOperation
import com.quata.feature.neighborhoods.domain.CommunityMutationSafety
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Browser read adapter for the Communities directory.
 *
 * It deliberately uses the same authenticated PostgREST client as the other Web repositories.
 * Community chat, follow and moderation writes are not exposed by the browser transport yet, so
 * they fail explicitly instead of inventing a local-only community state. SB-07 keeps every
 * Communities-owned mutation fail-closed through [CommunityMutationSafety] while RLS-001 is open.
 */
class WebNeighborhoodsRepository(
    private val client: WebPostgrestClient,
    private val authRepository: WebAuthRepository,
    private val pollIntervalMillis: Long = DefaultPollIntervalMillis,
) : NeighborhoodRepository {
    private val profileCache = mutableMapOf<String, CommunityUserProfile>()

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(loadCommunities())
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = unsupported("web_community_chat_not_implemented")

    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> =
        CommunityMutationSafety.blocked(CommunityMutationOperation.FollowUser)

    override suspend fun reportPost(postId: String): Result<Unit> =
        CommunityMutationSafety.blocked(CommunityMutationOperation.ReportPost)

    override suspend fun openPrivateChat(userId: String): Result<String> = unsupported("web_community_private_chat_not_implemented")

    override suspend fun isCurrentUserAdmin(): Boolean = runCatching {
        val userId = authenticatedUserId()
        loadProfiles(ids = listOf(userId)).firstOrNull()?.isAdmin == true
    }.getOrDefault(false)

    override suspend fun setUserRoles(userId: String, isAdmin: Boolean, isOfficial: Boolean): Result<NeighborhoodUser> =
        CommunityMutationSafety.blocked(CommunityMutationOperation.SetUserRoles)

    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?): CommunityUserProfile? = profileCache[userId]

    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        profileCache[profile.user.id] = profile
    }

    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(getUserProfile(userId))
            delay(pollIntervalMillis.coerceAtLeast(MinimumPollIntervalMillis))
        }
    }

    override suspend fun getUserProfile(userId: String): Result<CommunityUserProfile> = runCatching {
        require(userId.matches(PostgrestIdentifier)) { "web_community_invalid_profile_id" }
        val profile = loadProfiles(ids = listOf(userId)).firstOrNull()
            ?: error("web_community_profile_not_found")
        val enriched = profile.copy(isFollowing = false)
        // This read adapter has not yet enabled the posts endpoint; an empty collection is an
        // explicit snapshot of that endpoint rather than a fabricated profile timeline.
        CommunityUserProfile(user = enriched, posts = emptyList()).also { profileCache[userId] = it }
    }

    private suspend fun loadCommunities(): List<NeighborhoodCommunity> {
        val profiles = loadProfiles()
        val walls = client.rows(
            table = "community_walls_stats",
            query = mapOf(
                "select" to WallStatsSelect,
                "is_active" to "eq.true",
                "order" to "sort_order.asc,chat_last_at.desc,created_at.desc",
            ),
            limit = DirectoryLimit,
        ).map(JsonObject::toWallStats)
        val wallsByKey = walls.mapNotNull { wall ->
            (wall.normalizedName ?: wall.name?.normalizedCommunityKey())
                ?.takeIf(String::isNotBlank)
                ?.let { it to wall }
        }.toMap()
        val profilesByNeighborhood = profiles
            .filter { it.neighborhood.isNotBlank() }
            .groupBy { it.neighborhood.normalizedCommunityKey() }
        return (profilesByNeighborhood.keys + wallsByKey.keys)
            .filter(String::isNotBlank)
            .map { key ->
                val wall = wallsByKey[key]
                val users = profilesByNeighborhood[key].orEmpty().sortedBy { it.displayName.lowercase() }
                NeighborhoodCommunity(
                    name = wall?.name?.takeIf(String::isNotBlank)
                        ?: users.firstOrNull()?.neighborhood
                        ?: key,
                    users = users,
                    conversationId = null,
                    lastMessagePreview = null,
                    lastMessageAtMillis = wall?.chatLastAtMillis,
                    messageCount = wall?.chatCount ?: 0,
                )
            }
            .sortedWith(compareByDescending<NeighborhoodCommunity> { it.lastMessageAtMillis ?: 0L }.thenBy { it.name.lowercase() })
    }

    private suspend fun loadProfiles(ids: List<String>? = null): List<NeighborhoodUser> = client.rows(
        table = "community_profiles",
        query = buildMap {
            put("select", ProfileSelect)
            ids?.takeIf { it.isNotEmpty() }?.let { put("id", it.toPostgrestInFilter()) }
        },
        limit = DirectoryLimit,
    ).map(JsonObject::toNeighborhoodUser)

    private suspend fun authenticatedUserId(): String = authRepository.sessionForAuthenticatedRequest()?.userId
        ?: error("web_community_session_missing")

    private suspend fun WebPostgrestClient.rows(
        table: String,
        query: Map<String, String>,
        limit: Int,
    ): List<JsonObject> = when (val result = get(table, query, limit)) {
        is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray.map { it.jsonObject }
        is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
    }

    private fun <T> unsupported(reason: String): Result<T> = Result.failure(UnsupportedOperationException(reason))

    private companion object {
        const val DirectoryLimit = 500
        const val DefaultPollIntervalMillis = 30_000L
        const val MinimumPollIntervalMillis = 5_000L
        const val ProfileSelect = "id,display_name,phone,country_code,phone_local,barrio,neighborhood,telefono,nombre,avatar_url,avatar,followers_count,following_count,is_admin,is_official"
        const val WallStatsSelect = "id,slug,name,normalized_name,chat_count,chat_last_at"
        val PostgrestIdentifier = Regex("[A-Za-z0-9_-]+")
    }
}

private data class WebCommunityWallStats(
    val name: String?,
    val normalizedName: String?,
    val chatCount: Int?,
    val chatLastAtMillis: Long?,
)

private fun JsonObject.toNeighborhoodUser(): NeighborhoodUser {
    val id = webCommunityString("id") ?: error("web_community_response_missing_id")
    val neighborhood = webCommunityString("neighborhood") ?: webCommunityString("barrio").orEmpty()
    return NeighborhoodUser(
        id = id,
        displayName = webCommunityString("display_name") ?: webCommunityString("nombre") ?: "Usuario",
        email = webCommunityString("phone")
            ?: listOfNotNull(webCommunityString("country_code"), webCommunityString("phone_local")).joinToString("")
                .takeIf(String::isNotBlank).orEmpty(),
        neighborhood = neighborhood,
        avatarUrl = webCommunityString("avatar_url") ?: webCommunityString("avatar"),
        isAdmin = webCommunityString("is_admin") == "true",
        isOfficial = webCommunityString("is_official") == "true",
        followersCount = webCommunityInt("followers_count") ?: 0,
        followingCount = webCommunityInt("following_count") ?: 0,
    )
}

private fun JsonObject.toWallStats(): WebCommunityWallStats = WebCommunityWallStats(
    name = webCommunityString("name"),
    normalizedName = webCommunityString("normalized_name")?.normalizedCommunityKey(),
    chatCount = webCommunityInt("chat_count"),
    chatLastAtMillis = webCommunityString("chat_last_at")?.toWebCommunityEpochMillis(),
)

private fun JsonObject.webCommunityString(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.webCommunityInt(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

private fun String.normalizedCommunityKey(): String = trim().lowercase().replace(Regex("\\s+"), " ")

private fun List<String>.toPostgrestInFilter(): String = "in.(${joinToString(",")})"

private fun String.toWebCommunityEpochMillis(): Long? = browserDateParse(this)
    ?.takeIf { !it.isNaN() }
    ?.toLong()

private fun browserDateParse(value: String): Double? = js("Date.parse(value)")
