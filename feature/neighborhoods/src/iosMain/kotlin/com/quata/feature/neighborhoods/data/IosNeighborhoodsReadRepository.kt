package com.quata.feature.neighborhoods.data

import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.neighborhoods.domain.CommunityUserProfile
import com.quata.feature.neighborhoods.domain.CommunityMutationOperation
import com.quata.feature.neighborhoods.domain.CommunityMutationSafety
import com.quata.feature.neighborhoods.domain.FollowUserResult
import com.quata.feature.neighborhoods.domain.NeighborhoodCommunity
import com.quata.feature.neighborhoods.domain.NeighborhoodRepository
import com.quata.feature.neighborhoods.domain.NeighborhoodUser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreFoundation.CFDataCreate
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSNull
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Client-safe configuration injected by the UIKit composition root. */
data class IosNeighborhoodsRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
)

/**
 * Authenticated iOS read adapter for the common Communities directory.
 *
 * It uses URLSession and the same renewable Keychain session as Auth/Feed/Chat. Directory and
 * profile reads are real PostgREST snapshots; there is deliberately no fabricated local data or
 * unverified realtime subscription. Follow, moderation and report mutations remain explicit
 * failures until their RLS contracts have iOS E2E evidence. SB-07 additionally keeps every
 * Communities-owned mutation fail-closed through [CommunityMutationSafety] while RLS-001 is open.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNeighborhoodsReadRepository(
    private val configuration: IosNeighborhoodsRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
    private val chatRepository: ChatRepository,
) : NeighborhoodRepository {
    private val profileCache = mutableMapOf<String, CommunityUserProfile>()

    override fun observeCommunities(): Flow<List<NeighborhoodCommunity>> = flow {
        emit(loadCommunities())
    }

    override suspend fun openNeighborhoodChat(neighborhood: String): Result<String> = runCatching {
        val cleanNeighborhood = neighborhood.trim().takeIf(String::isNotEmpty)
            ?: error("ios_communities_neighborhood_missing")
        val session = authenticatedSession()
        chatRepository.cachedCommunityConversationId(cleanNeighborhood)
            ?: chatRepository.openCommunityConversation(
                communityId = cleanNeighborhood.normalizedCommunityKey(),
                title = cleanNeighborhood,
                participantIds = listOf(session.userId),
            ).getOrThrow()
    }

    override suspend fun toggleFollowUser(userId: String): Result<FollowUserResult> =
        CommunityMutationSafety.blocked(CommunityMutationOperation.FollowUser)

    override suspend fun reportPost(postId: String): Result<Unit> =
        CommunityMutationSafety.blocked(CommunityMutationOperation.ReportPost)

    override suspend fun openPrivateChat(userId: String): Result<String> = runCatching {
        require(userId.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
        chatRepository.cachedPrivateConversationId(userId)
            ?: chatRepository.openPrivateConversation(userId).getOrThrow()
    }

    override suspend fun isCurrentUserAdmin(): Boolean = runCatching {
        val session = authenticatedSession()
        loadProfiles(listOf(session.userId)).firstOrNull()?.isAdmin == true
    }.getOrDefault(false)

    override suspend fun setUserRoles(
        userId: String,
        isAdmin: Boolean,
        isOfficial: Boolean,
    ): Result<NeighborhoodUser> = CommunityMutationSafety.blocked(CommunityMutationOperation.SetUserRoles)

    override suspend fun getCachedUserProfile(userId: String, maxAgeMillis: Long?): CommunityUserProfile? = profileCache[userId]

    override suspend fun cacheUserProfile(profile: CommunityUserProfile) {
        profileCache[profile.user.id] = profile
    }

    override fun observeUserProfile(userId: String): Flow<Result<CommunityUserProfile>> = flow {
        emit(getUserProfile(userId))
    }

    override suspend fun getUserProfile(userId: String): Result<CommunityUserProfile> = runCatching {
        require(userId.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
        val user = loadProfiles(listOf(userId)).firstOrNull() ?: error("ios_communities_profile_not_found")
        // The iOS endpoint is verified only for directory/profile reads. Do not infer a timeline
        // from unrelated Feed endpoints until its profile-post contract is exercised.
        CommunityUserProfile(user = user, posts = emptyList()).also { profileCache[userId] = it }
    }

    private suspend fun loadCommunities(): List<NeighborhoodCommunity> {
        val grouped = loadProfiles()
            .filter { it.neighborhood.isNotBlank() }
            .groupBy { it.neighborhood.normalizedCommunityKey() }
        return buildList {
            grouped.forEach { (_, users) ->
                val name = users.firstOrNull()?.neighborhood?.takeIf(String::isNotBlank) ?: return@forEach
                add(
                    NeighborhoodCommunity(
                        name = name,
                        users = users.distinctBy(NeighborhoodUser::id).sortedBy { it.displayName.lowercase() },
                        conversationId = chatRepository.cachedCommunityConversationId(name),
                        lastMessagePreview = null,
                        lastMessageAtMillis = null,
                        messageCount = 0,
                    ),
                )
            }
        }.sortedBy { it.name.lowercase() }
    }

    private suspend fun loadProfiles(ids: List<String>? = null): List<NeighborhoodUser> = rows(
        query = buildMap {
            put("select", ProfileSelect)
            put("order", "display_name.asc")
            put("limit", DirectoryLimit.toString())
            ids?.takeIf { it.isNotEmpty() }?.let { put("id", it.toIosNeighborhoodInFilter()) }
        },
    ).map(Map<*, *>::toIosNeighborhoodUser)

    private suspend fun rows(query: Map<String, String>): List<Map<*, *>> {
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty)
            ?: error("ios_communities_supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: error("ios_communities_supabase_publishable_key_missing")
        val session = authenticatedSession()
        val url = NSURL(string = "$baseUrl/rest/v1/community_profiles${query.toIosNeighborhoodQueryString()}")
            ?: error("ios_communities_url_invalid")
        val requestConfiguration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            HTTPAdditionalHeaders = mapOf(
                "apikey" to publishableKey,
                "Authorization" to "Bearer ${session.bearerToken}",
                "Accept" to "application/json",
            )
        }
        val data = requestConfiguration.iosNeighborhoodData(url)
        val root = NSJSONSerialization.JSONObjectWithData(data, options = 0u, error = null) as? List<*>
            ?: error("ios_communities_response_not_array")
        return root.mapIndexed { index, row ->
            row as? Map<*, *> ?: error("ios_communities_response_row_${index}_invalid")
        }
    }

    private suspend fun authenticatedSession() = authSession.currentSession()
        ?.takeIf { it.bearerToken.isNotBlank() && it.userId.isNotBlank() }
        ?: error("ios_communities_session_missing")

    private companion object {
        const val DirectoryLimit = 500
        const val ProfileSelect = "id,display_name,phone,country_code,phone_local,barrio,neighborhood,telefono,nombre,avatar_url,avatar,followers_count,following_count,is_admin,is_official"
    }
}

/** Small iOS composition factory; UIKit owns navigation and system-only affordances. */
class IosNeighborhoodsRuntimeBootstrap(
    configuration: IosNeighborhoodsRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
    chatRepository: ChatRepository,
) {
    val repository: NeighborhoodRepository = IosNeighborhoodsReadRepository(configuration, authSession, chatRepository)

    fun restoredCurrentUserId(): String? = authSession.restoredSession()?.userId?.takeIf(String::isNotBlank)
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLSessionConfiguration.iosNeighborhoodData(url: NSURL): NSData = suspendCancellableCoroutine { continuation ->
    val delegate = IosNeighborhoodDataTaskDelegate(continuation)
    val session = NSURLSession.sessionWithConfiguration(this, delegate, null)
    val task = session.dataTaskWithRequest(NSURLRequest(url))
    continuation.invokeOnCancellation {
        task.cancel()
        session.invalidateAndCancel()
    }
    task.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class IosNeighborhoodDataTaskDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toIosNeighborhoodBytes()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException("ios_communities_network:${didCompleteWithError.localizedDescription}"))
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(IllegalStateException("ios_communities_http_${status ?: "unknown"}"))
            return
        }
        continuation.resume(chunks.toIosNeighborhoodData())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosNeighborhoodBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun List<ByteArray>.toIosNeighborhoodData(): NSData {
    val total = sumOf { it.size }
    if (total == 0) return NSData()
    val merged = ByteArray(total)
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(merged, destinationOffset = offset)
        offset += chunk.size
    }
    return merged.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), merged.size.toLong())!! as NSData
    }
}

private fun Map<*, *>.toIosNeighborhoodUser(): NeighborhoodUser = NeighborhoodUser(
    id = requiredIosNeighborhoodString("id"),
    displayName = iosNeighborhoodString("display_name") ?: iosNeighborhoodString("nombre") ?: "Quata user",
    email = iosNeighborhoodString("phone")
        ?: listOfNotNull(iosNeighborhoodString("country_code"), iosNeighborhoodString("phone_local"))
            .joinToString("").takeIf(String::isNotBlank).orEmpty(),
    neighborhood = iosNeighborhoodString("neighborhood") ?: iosNeighborhoodString("barrio").orEmpty(),
    avatarUrl = iosNeighborhoodString("avatar_url") ?: iosNeighborhoodString("avatar"),
    isAdmin = iosNeighborhoodBoolean("is_admin"),
    isOfficial = iosNeighborhoodBoolean("is_official"),
    followersCount = iosNeighborhoodInt("followers_count"),
    followingCount = iosNeighborhoodInt("following_count"),
)

private fun Map<*, *>.requiredIosNeighborhoodString(name: String): String =
    iosNeighborhoodString(name) ?: error("ios_communities_response_missing_$name")

private fun Map<*, *>.iosNeighborhoodString(name: String): String? = this[name]
    ?.takeUnless { it is NSNull }
    ?.toString()
    ?.takeIf(String::isNotBlank)

private fun Map<*, *>.iosNeighborhoodBoolean(name: String): Boolean = when (iosNeighborhoodString(name)?.lowercase()) {
    "true", "1", "yes" -> true
    else -> false
}

private fun Map<*, *>.iosNeighborhoodInt(name: String): Int = iosNeighborhoodString(name)?.toIntOrNull() ?: 0

private fun Collection<String>.toIosNeighborhoodInFilter(): String = "in.(${distinct().joinToString(",") {
    require(it.matches(IosNeighborhoodIdentifier)) { "ios_communities_profile_id_invalid" }
    it
}})"

private fun Map<String, String>.toIosNeighborhoodQueryString(): String = entries.joinToString(prefix = "?", separator = "&") { (key, value) ->
    "${key.toIosNeighborhoodQueryComponent()}=${value.toIosNeighborhoodQueryComponent()}"
}

private fun String.toIosNeighborhoodQueryComponent(): String = encodeToByteArray().joinToString("") { byte ->
    val value = byte.toInt() and 0xff
    if ((value in 'a'.code..'z'.code) || (value in 'A'.code..'Z'.code) || (value in '0'.code..'9'.code) || value in intArrayOf('-'.code, '.'.code, '_'.code, '~'.code)) {
        value.toChar().toString()
    } else "%${value.toString(16).padStart(2, '0').uppercase()}"
}

private fun String.normalizedCommunityKey(): String = trim().lowercase().replace(Regex("\\s+"), " ")

private val IosNeighborhoodIdentifier = Regex("[A-Za-z0-9_-]+")
