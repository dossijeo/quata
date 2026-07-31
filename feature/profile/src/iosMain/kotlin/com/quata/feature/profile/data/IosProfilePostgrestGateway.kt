package com.quata.feature.profile.data

import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.data.toFoundationData
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSNull
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
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

internal data class IosProfileHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
)

internal fun interface IosProfileHttpTransport {
    suspend fun execute(request: IosProfileHttpRequest): NSData
}

private object IosUrlSessionProfileHttpTransport : IosProfileHttpTransport {
    override suspend fun execute(request: IosProfileHttpRequest): NSData {
        val url = NSURL(string = request.url) ?: error("ios_profile_url_invalid")
        val native = NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod(request.method)
            request.headers.forEach { (name, value) -> setValue(value, name) }
            request.body?.let { setHTTPBody(it.encodeToByteArray().toFoundationData()) }
        }
        return NSURLSessionConfiguration.ephemeralSessionConfiguration().iosProfileData(native)
    }
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
 * Authenticated PostgREST adapter for the shared Profile repository.
 *
 * The request session is obtained from the existing renewable Keychain-backed owner for every
 * snapshot. The `observe*` methods intentionally emit one network snapshot and finish: Profile
 * has no verified Realtime contract yet, so this adapter must not pretend that polling or a local
 * cache is a live subscription. Mutations mirror Android's current direct authenticated access;
 * actor matching remains a client compatibility guard while the bridge/RLS rollout is pending.
 */
@OptIn(ExperimentalForeignApi::class)
class IosProfilePostgrestGateway internal constructor(
    private val configuration: IosProfileRuntimeConfiguration,
    private val sessionProvider: IosProfileSessionProvider,
    private val transport: IosProfileHttpTransport,
) : ProfileRemoteGateway {
    constructor(
        configuration: IosProfileRuntimeConfiguration,
        sessionProvider: IosProfileSessionProvider,
    ) : this(configuration, sessionProvider, IosUrlSessionProfileHttpTransport)

    constructor(
        configuration: IosProfileRuntimeConfiguration,
        authSession: IosRenewableAuthSession,
    ) : this(configuration, IosProfileKeychainSessionProvider(authSession), IosUrlSessionProfileHttpTransport)

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

    override suspend fun saveProfile(profileId: String, patch: Map<String, String?>) {
        requireIosProfileActor(profileId, sessionProvider.currentSession()?.profileId)
        val allowed = patch.filterKeys { it in IosProfileWritableColumns }
        require(allowed.isNotEmpty()) { "ios_profile_patch_empty" }
        mutate(CommunityProfilesTable, "PATCH", mapOf("id" to "eq.$profileId"), allowed.toIosProfileJson())
    }

    override suspend fun saveRecoverySecret(profileId: String, secretQuestion: String, secretAnswer: String) {
        profileId.requireIosProfileIdentifier()
        require(secretQuestion.isNotBlank() && secretAnswer.isNotBlank()) { "ios_profile_recovery_secret_required" }
        authenticatedFunction("quata-auth-bridge", iosProfileRecoverySecretBody(secretQuestion, secretAnswer))
    }

    override suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) {
        requireIosProfileActor(profileId, sessionProvider.currentSession()?.profileId)
        val normalized = contactIds.map { it.requireIosProfileIdentifier() }.distinct().take(MaxEmergencyContacts)
        mutate(CommunityEmergencyContactsTable, "DELETE", mapOf("profile_id" to "eq.$profileId"), null)
        if (normalized.isNotEmpty()) {
            val rows = normalized.mapIndexed { index, id -> "{\"profile_id\":${profileId.toIosProfileJsonString()},\"contact_profile_id\":${id.toIosProfileJsonString()},\"position\":${index + 1}}" }
                .joinToString(separator = ",", prefix = "[", postfix = "]")
            mutate(CommunityEmergencyContactsTable, "POST", emptyMap(), rows)
        }
    }

    private suspend fun getRows(table: String, query: Map<String, String>): List<Map<*, *>> {
        require(table.matches(IosProfilePostgrestTableName)) { "ios_profile_postgrest_table_invalid" }
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty)
            ?: error("ios_profile_supabase_url_missing")
        val publishableKey = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty)
            ?: error("ios_profile_supabase_publishable_key_missing")
        val session = sessionProvider.currentSession()?.takeIf { it.accessToken.isNotBlank() }
            ?: error("ios_profile_session_missing")
        val request = IosProfileHttpRequest(
            method = "GET",
            url = "$baseUrl/rest/v1/$table${query.toIosProfileQueryString()}",
            headers = mapOf(
                "apikey" to publishableKey,
                "Authorization" to "Bearer ${session.accessToken}",
                "Accept" to "application/json",
            ),
        )
        return transport.execute(request).toIosProfileRows()
    }

    private suspend fun authenticatedFunction(function: String, body: String) {
        val session = sessionProvider.currentSession()?.takeIf { it.accessToken.isNotBlank() } ?: error("ios_profile_session_missing")
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/').takeIf(String::isNotEmpty) ?: error("ios_profile_supabase_url_missing")
        val key = configuration.supabasePublishableKey.trim().takeIf(String::isNotEmpty) ?: error("ios_profile_supabase_publishable_key_missing")
        val request = IosProfileHttpRequest(
            method = "POST",
            url = "$baseUrl/functions/v1/$function",
            headers = mapOf("apikey" to key, "Authorization" to "Bearer ${session.accessToken}", "Content-Type" to "application/json"),
            body = body,
        )
        val response = transport.execute(request)
        check(iosProfileFunctionResponseIsOk(response.toIosProfileBytes().decodeToString())) {
            "ios_profile_function_response_invalid"
        }
    }

    private suspend fun mutate(table: String, method: String, query: Map<String, String>, body: String?) {
        val session = sessionProvider.currentSession()?.takeIf { it.accessToken.isNotBlank() } ?: error("ios_profile_session_missing")
        val baseUrl = configuration.supabaseUrl.trim().trimEnd('/')
        val key = configuration.supabasePublishableKey.trim()
        val request = IosProfileHttpRequest(
            method = method,
            url = "$baseUrl/rest/v1/$table${query.toIosProfileQueryString()}",
            headers = buildMap {
                put("apikey", key)
                put("Authorization", "Bearer ${session.accessToken}")
                put("Accept", "application/json")
                if (body != null) put("Content-Type", "application/json")
            },
            body = body,
        )
        transport.execute(request)
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
internal suspend fun NSURLSessionConfiguration.iosProfileData(request: NSMutableURLRequest): NSData =
    suspendCancellableCoroutine { continuation ->
        val delegate = IosProfileDataTaskDelegate(continuation)
        val session = NSURLSession.sessionWithConfiguration(this, delegate, null)
        val task = session.dataTaskWithRequest(request)
        continuation.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }
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
    if (all(ByteArray::isEmpty)) return null
    return toFoundationData()
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

internal fun requireIosProfileActor(profileId: String, sessionProfileId: String?): String {
    val normalized = profileId.requireIosProfileIdentifier()
    check(sessionProfileId?.requireIosProfileIdentifier() == normalized) { "ios_profile_actor_mismatch" }
    return normalized
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

private fun Map<String, String?>.toIosProfileJson(): String = entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
    "${key.toIosProfileJsonString()}:${value?.toIosProfileJsonString() ?: "null"}"
}

private fun String.toIosProfileJsonString(): String = buildString {
    append('"')
    forEach { c -> when (c) { '\\' -> append("\\\\"); '"' -> append("\\\""); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t"); else -> if (c.code < 0x20) append("\\u${c.code.toString(16).padStart(4, '0')}") else append(c) } }
    append('"')
}

internal fun iosProfileRecoverySecretBody(secretQuestion: String, secretAnswer: String): String =
    "{\"version\":1,\"action\":\"update_recovery_secret\",\"secret_question\":${secretQuestion.trim().toIosProfileJsonString()},\"secret_answer\":${secretAnswer.toIosProfileJsonString()}}"

@OptIn(ExperimentalForeignApi::class)
internal fun iosProfileFunctionResponseIsOk(body: String): Boolean = runCatching {
    val value = NSJSONSerialization.JSONObjectWithData(
        body.encodeToByteArray().toFoundationData(),
        options = 0u,
        error = null,
    ) as? Map<*, *>
    value?.get("ok") as? Boolean == true
}.getOrDefault(false)

private const val CommunityProfilesTable = "community_profiles"
private const val CommunityEmergencyContactsTable = "community_emergency_contacts"
private const val ProfileSelect = "id,display_name,phone,country_code,phone_local,phone_e164,barrio,neighborhood,code,telefono,nombre,avatar_url,avatar,secret_question"
private const val EmergencyContactSelect = "contact_profile_id,position"
private const val MaxProfilesPerSnapshot = 5_000
private const val MaxEmergencyContacts = 5
private val IosProfilePostgrestTableName = Regex("[A-Za-z_][A-Za-z0-9_]*")
private val IosProfileIdentifier = Regex("[A-Za-z0-9_-]+")
private val IosProfileWritableColumns = setOf("display_name", "nombre", "neighborhood", "barrio", "country_code", "code", "phone_local", "phone", "telefono", "avatar_url")
