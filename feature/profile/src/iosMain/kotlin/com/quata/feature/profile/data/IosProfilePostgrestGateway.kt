package com.quata.feature.profile.data

import com.quata.core.session.IosRenewableAuthSession
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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

/** Client-safe deployment settings for Profile's iOS PostgREST boundary. */
data class IosProfileRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
)

/** A renewable authenticated identity supplied by the iOS composition root for each request. */
data class IosProfileSession(
    val accessToken: String,
    val profileId: String,
)

/** Makes session resolution replaceable without exposing Keychain or Auth implementation details. */
fun interface IosProfileSessionProvider {
    suspend fun currentSession(): IosProfileSession?
}

/** Adapts the established Keychain-backed session owner to Profile's narrow read contract. */
class IosProfileKeychainSessionProvider(
    private val authSession: IosRenewableAuthSession,
) : IosProfileSessionProvider {
    override suspend fun currentSession(): IosProfileSession? = authSession.currentSession()
        ?.takeIf { it.bearerToken.isNotBlank() && it.userId.isNotBlank() }
        ?.let { IosProfileSession(accessToken = it.bearerToken, profileId = it.userId) }
}

/**
 * Authenticated, read-only PostgREST adapter for the shared Profile repository.
 *
 * The request session is obtained from the existing renewable Keychain-backed owner for every
 * snapshot. The `observe*` methods intentionally emit one network snapshot and finish: Profile
 * has no verified Realtime contract yet, so this adapter must not pretend that polling or a local
 * cache is a live subscription. Mutations fail explicitly until their RLS policies and endpoint
 * contract have been exercised on iOS.
 */
@OptIn(ExperimentalForeignApi::class)
class IosProfilePostgrestGateway(
    private val configuration: IosProfileRuntimeConfiguration,
    private val sessionProvider: IosProfileSessionProvider,
) : ProfileRemoteGateway {
    constructor(
        configuration: IosProfileRuntimeConfiguration,
        authSession: IosRenewableAuthSession,
    ) : this(configuration, IosProfileKeychainSessionProvider(authSession))

    override suspend fun getProfile(profileId: String): ProfileRemoteRecord? =
        getProfiles(listOf(profileId)).firstOrNull()

    override suspend fun getProfiles(profileIds: Collection<String>): List<ProfileRemoteRecord> {
        if (profileIds.isEmpty()) return emptyList()
        val distinctProfileIds = profileIds.distinct()
        require(distinctProfileIds.size <= MaxProfilesPerSnapshot) { "ios_profile_snapshot_limit_exceeded" }
        return getRows(
            table = CommunityProfilesTable,
            query = mapOf(
                "select" to ProfileSelect,
                "id" to distinctProfileIds.toIosProfileInFilter(),
                "limit" to distinctProfileIds.size.toString(),
            ),
        ).map { it.toProfileRemoteRecord() }
    }

    override fun observeProfile(profileId: String): Flow<ProfileRemoteRecord?> = flow {
        emit(getProfile(profileId))
    }

    override suspend fun getEmergencyCandidates(): List<ProfileRemoteRecord> = getRows(
        table = CommunityProfilesTable,
        query = mapOf(
            "select" to ProfileSelect,
            "order" to "display_name.asc",
            "limit" to MaxProfilesPerSnapshot.toString(),
        ),
    ).map { it.toProfileRemoteRecord() }

    override fun observeEmergencyCandidates(): Flow<List<ProfileRemoteRecord>> = flow {
        emit(getEmergencyCandidates())
    }

    override suspend fun getEmergencyContactIds(
        profileId: String,
        cachePolicy: ProfileCachePolicy,
    ): List<String> {
        // There is deliberately no fabricated iOS cache. Both policies perform an authenticated
        // snapshot until a tested persistent cache is introduced above this transport boundary.
        @Suppress("UNUSED_VARIABLE") val requestedPolicy = cachePolicy
        return getRows(
            table = CommunityEmergencyContactsTable,
            query = mapOf(
                "select" to EmergencyContactSelect,
                "profile_id" to "eq.${profileId.requireIosProfileIdentifier()}",
                "order" to "position.asc,created_at.asc",
                "limit" to MaxEmergencyContacts.toString(),
            ),
        ).mapNotNull { it.iosString("contact_profile_id") }
            .distinct()
            .take(MaxEmergencyContacts)
    }

    override suspend fun saveProfile(@Suppress("UNUSED_PARAMETER") profileId: String, @Suppress("UNUSED_PARAMETER") patch: Map<String, String?>): Nothing =
        throw UnsupportedOperationException("ios_profile_mutations_not_verified")

    override suspend fun saveEmergencyContacts(@Suppress("UNUSED_PARAMETER") profileId: String, @Suppress("UNUSED_PARAMETER") contactIds: List<String>): Nothing =
        throw UnsupportedOperationException("ios_profile_emergency_contacts_mutations_not_verified")

    private suspend fun getRows(table: String, query: Map<String, String>): List<Map<*, *>> {
        require(table.matches(IosProfilePostgrestTableName)) { "ios_profile_postgrest_table_invalid" }
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty)
            ?: error("ios_profile_supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: error("ios_profile_supabase_publishable_key_missing")
        val session = sessionProvider.currentSession()?.takeIf { it.accessToken.isNotBlank() }
            ?: error("ios_profile_session_missing")
        val url = NSURL(string = "$baseUrl/rest/v1/$table${query.toIosProfileQueryString()}")
            ?: error("ios_profile_url_invalid")
        val requestConfiguration = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            HTTPAdditionalHeaders = mapOf(
                "apikey" to publishableKey,
                "Authorization" to "Bearer ${session.accessToken}",
                "Accept" to "application/json",
            )
        }
        return requestConfiguration.iosProfileData(url).toIosProfileRows()
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun NSURLSessionConfiguration.iosProfileData(url: NSURL): NSData =
    suspendCancellableCoroutine { continuation ->
        val delegate = IosProfileDataTaskDelegate(continuation)
        val session = NSURLSession.sessionWithConfiguration(this, delegate, null)
        val task = session.dataTaskWithRequest(NSURLRequest(url))
        continuation.invokeOnCancellation {
            task.cancel()
            session.invalidateAndCancel()
        }
        task.resume()
    }

@OptIn(ExperimentalForeignApi::class)
private class IosProfileDataTaskDelegate(
    private val continuation: CancellableContinuation<NSData>,
) : NSObject(), NSURLSessionDataDelegateProtocol {
    private val chunks = mutableListOf<ByteArray>()

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        if (continuation.isActive) chunks += didReceiveData.toIosProfileBytes()
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        session.finishTasksAndInvalidate()
        if (!continuation.isActive) return
        if (didCompleteWithError != null) {
            continuation.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription))
            return
        }
        val status = (task.response as? NSHTTPURLResponse)?.statusCode?.toInt()
        if (status == null || status !in 200..299) {
            continuation.resumeWithException(IllegalStateException("ios_profile_http_${status ?: "unknown"}"))
            return
        }
        continuation.resume(chunks.toIosProfileDataOrNull() ?: NSData())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosProfileBytes(): ByteArray =
    if (length == 0uL) ByteArray(0) else bytes?.readBytes(length.toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
private fun List<ByteArray>.toIosProfileDataOrNull(): NSData? {
    val totalSize = sumOf { it.size }
    if (totalSize == 0) return null
    val merged = ByteArray(totalSize)
    var offset = 0
    forEach { chunk ->
        chunk.copyInto(merged, destinationOffset = offset)
        offset += chunk.size
    }
    return merged.usePinned { pinned ->
        CFDataCreate(null, pinned.addressOf(0).reinterpret(), merged.size.toLong())!! as NSData
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toIosProfileRows(): List<Map<*, *>> {
    val json = NSJSONSerialization.JSONObjectWithData(this, options = 0u, error = null) as? List<*>
        ?: error("ios_profile_response_not_array")
    return json.map { row -> row as? Map<*, *> ?: error("ios_profile_response_row_invalid") }
}

private fun Map<*, *>.toProfileRemoteRecord(): ProfileRemoteRecord = ProfileRemoteRecord(
    id = requiredIosProfileString("id"),
    displayName = iosString("display_name"),
    legacyName = iosString("nombre"),
    neighborhood = iosString("neighborhood"),
    legacyNeighborhood = iosString("barrio"),
    countryCode = iosString("country_code"),
    legacyCountryCode = iosString("code"),
    phoneLocal = iosString("phone_local"),
    phoneE164 = iosString("phone_e164"),
    phone = iosString("phone"),
    legacyPhone = iosString("telefono"),
    avatarUrl = iosString("avatar_url"),
    legacyAvatar = iosString("avatar"),
    secretQuestion = iosString("secret_question"),
)

private fun Map<*, *>.requiredIosProfileString(name: String): String =
    iosString(name) ?: error("ios_profile_response_missing_$name")

private fun Map<*, *>.iosString(name: String): String? = this[name]
    ?.takeUnless { it is NSNull }
    ?.toString()

private fun Collection<String>.toIosProfileInFilter(): String = "in.(${distinct().joinToString(",") {
    it.requireIosProfileIdentifier()
}})"

private fun String.requireIosProfileIdentifier(): String {
    require(matches(IosProfileIdentifier)) { "ios_profile_invalid_postgrest_identifier" }
    return this
}

private fun Map<String, String>.toIosProfileQueryString(): String = entries.joinToString(prefix = "?", separator = "&") { (key, value) ->
    "${key.toIosProfileQueryComponent()}=${value.toIosProfileQueryComponent()}"
}

private fun String.toIosProfileQueryComponent(): String = encodeToByteArray().joinToString(separator = "") { byte ->
    val value = byte.toInt() and 0xff
    if ((value in 'a'.code..'z'.code) || (value in 'A'.code..'Z'.code) || (value in '0'.code..'9'.code) || value in intArrayOf('-'.code, '.'.code, '_'.code, '~'.code)) {
        value.toChar().toString()
    } else {
        "%${value.toString(16).padStart(2, '0').uppercase()}"
    }
}

private const val CommunityProfilesTable = "community_profiles"
private const val CommunityEmergencyContactsTable = "community_emergency_contacts"
private const val ProfileSelect = "id,display_name,phone,country_code,phone_local,phone_e164,barrio,neighborhood,code,telefono,nombre,avatar_url,avatar,secret_question"
private const val EmergencyContactSelect = "contact_profile_id,position"
private const val MaxProfilesPerSnapshot = 5_000
private const val MaxEmergencyContacts = 5
private val IosProfilePostgrestTableName = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val IosProfileIdentifier = Regex("[A-Za-z0-9_-]+")
