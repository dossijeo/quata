package com.quata.web

import com.quata.feature.profile.data.ProfileCachePolicy
import com.quata.feature.profile.data.ProfileRemoteGateway
import com.quata.feature.profile.data.ProfileRemoteRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Authenticated browser transport for Profile's portable repository.
 *
 * [WebPostgrestClient] owns the injected Web session and sends its bearer token on every read.
 * It deliberately exposes one-shot flows rather than pretending the browser has a realtime
 * subscription. A future realtime adapter can replace these flows without changing Profile's
 * common contract.
 *
 * Reads use PostgREST. Writes are deliberately fail-closed: profile tables currently have no
 * verified server-bound actor contract, so this client must never emit PATCH/POST/DELETE directly.
 * A future `quata-profile-bridge` is the only permitted mutation boundary.
 */
class WebProfileRemoteGateway(
    private val client: WebPostgrestClient,
    private val authRepository: WebAuthRepository,
) : ProfileRemoteGateway {
    override suspend fun getProfile(profileId: String): ProfileRemoteRecord? {
        requireProfileId(profileId)
        return loadProfiles(ids = listOf(profileId), limit = 1).firstOrNull()
    }

    override suspend fun getProfiles(profileIds: Collection<String>): List<ProfileRemoteRecord> {
        if (profileIds.isEmpty()) return emptyList()
        return loadProfiles(ids = profileIds, limit = profileIds.size.coerceAtMost(ProfileDirectoryLimit))
    }

    override fun observeProfile(profileId: String): Flow<ProfileRemoteRecord?> = flow {
        emit(getProfile(profileId))
    }

    override suspend fun getEmergencyCandidates(): List<ProfileRemoteRecord> =
        loadProfiles(limit = ProfileDirectoryLimit)

    override fun observeEmergencyCandidates(): Flow<List<ProfileRemoteRecord>> = flow {
        emit(getEmergencyCandidates())
    }

    override suspend fun getEmergencyContactIds(
        profileId: String,
        cachePolicy: ProfileCachePolicy,
    ): List<String> {
        requireProfileId(profileId)
        // The browser transport does not maintain a cache. Both policies therefore perform the
        // same authenticated network read; the repository's injected local store remains the
        // offline-first cache.
        @Suppress("UNUSED_VARIABLE") val ignoredCachePolicy = cachePolicy
        return client.rows(
            table = EmergencyContactsTable,
            query = mapOf(
                "select" to "contact_profile_id",
                "profile_id" to "eq.$profileId",
                "order" to "position.asc,created_at.asc",
            ),
            limit = MaxEmergencyContacts,
        ).mapNotNull { it.profileString("contact_profile_id") }
            .distinct()
            .take(MaxEmergencyContacts)
    }

    override suspend fun saveProfile(profileId: String, patch: Map<String, String?>) {
        requireWebProfileActor(profileId, authRepository.sessionForAuthenticatedRequest()?.userId)
        val allowed = patch.filterKeys { it in ProfileWritableColumns }
        require(allowed.isNotEmpty()) { "web_profile_patch_empty" }
        client.patch(ProfilesTable, mapOf("id" to "eq.$profileId"), allowed.toPostgrestProfileJson()).requireProfileMutationSuccess("patch")
    }

    override suspend fun saveRecoverySecret(
        profileId: String,
        @Suppress("UNUSED_PARAMETER") secretQuestion: String,
        @Suppress("UNUSED_PARAMETER") secretAnswer: String,
    ) {
        requireWebProfileActor(profileId, authRepository.sessionForAuthenticatedRequest()?.userId)
        authRepository.updateRecoverySecret(secretQuestion, secretAnswer).getOrElse { throw it }
    }


    override suspend fun saveEmergencyContacts(
        profileId: String,
        contactIds: List<String>,
    ) {
        requireWebProfileActor(profileId, authRepository.sessionForAuthenticatedRequest()?.userId)
        val normalized = contactIds.map { it.requireProfileIdentifier() }.distinct().take(MaxEmergencyContacts)
        client.delete(EmergencyContactsTable, mapOf("profile_id" to "eq.$profileId")).requireProfileMutationSuccess("delete_contacts")
        if (normalized.isNotEmpty()) {
            val body = normalized.mapIndexed { index, id -> buildJsonObject { put("profile_id", profileId); put("contact_profile_id", id); put("position", index + 1) } }.joinToString("[", "]")
            client.post(EmergencyContactsTable, body).requireProfileMutationSuccess("post_contacts")
        }
    }

    private suspend fun loadProfiles(
        ids: Collection<String>? = null,
        limit: Int,
    ): List<ProfileRemoteRecord> {
        ids?.forEach(::requireProfileId)
        return client.rows(
            table = ProfilesTable,
            query = buildMap {
                put("select", ProfileSelect)
                ids?.takeIf { it.isNotEmpty() }?.let { put("id", it.toPostgrestInFilter()) }
            },
            limit = limit,
        ).map(JsonObject::toProfileRemoteRecord)
    }

    private suspend fun WebPostgrestClient.rows(
        table: String,
        query: Map<String, String>,
        limit: Int,
    ): List<JsonObject> = when (val result = get(table, query, limit)) {
        is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray.map { it.jsonObject }
        is WebPostgrestResult.Failure -> throw WebPostgrestReadException(result)
    }

    private companion object {
        const val ProfilesTable = "community_profiles"
        const val EmergencyContactsTable = "community_emergency_contacts"
        const val ProfileDirectoryLimit = 500
        const val MaxEmergencyContacts = 5
        const val ProfileSelect =
            "id,display_name,nombre,neighborhood,barrio,country_code,code,phone_local,phone_e164,phone,telefono,avatar_url,avatar,secret_question"
        val ProfileWritableColumns = setOf(
            "display_name", "nombre", "neighborhood", "barrio", "country_code", "code",
            "phone_local", "phone", "telefono", "avatar_url",
        )
    }
}

private fun Map<String, String?>.toPostgrestProfileJson(): String = buildJsonObject {
    forEach { (key, value) -> if (value == null) put(key, JsonNull) else put(key, value) }
}.toString()

internal fun WebPostgrestResult.requireProfileMutationSuccess(operation: String) {
    when (this) {
        is WebPostgrestResult.Success -> Unit
        is WebPostgrestResult.Failure -> throw IllegalStateException("web_profile_${operation}_${kind.name.lowercase()}_${statusCode ?: "network"}")
    }
}

private val WebProfileIdentifier = Regex("[A-Za-z0-9_-]+")

private fun Collection<String>.toPostgrestInFilter(): String =
    "in.(${distinct().joinToString(",") { it.requireProfileIdentifier() }})"

private fun requireProfileId(value: String) {
    value.requireProfileIdentifier()
}

/** Pure gate: a browser session may only mutate its own profile row. */
internal fun requireWebProfileActor(profileId: String, sessionProfileId: String?): String {
    val normalized = profileId.requireProfileIdentifier()
    check(sessionProfileId?.requireProfileIdentifier() == normalized) { "web_profile_actor_mismatch" }
    return normalized
}

private fun String.requireProfileIdentifier(): String {
    require(matches(WebProfileIdentifier)) { "web_profile_invalid_profile_id" }
    return this
}

private fun JsonObject.toProfileRemoteRecord(): ProfileRemoteRecord = ProfileRemoteRecord(
    id = profileString("id") ?: error("web_profile_response_missing_id"),
    displayName = profileString("display_name"),
    legacyName = profileString("nombre"),
    neighborhood = profileString("neighborhood"),
    legacyNeighborhood = profileString("barrio"),
    countryCode = profileString("country_code"),
    legacyCountryCode = profileString("code"),
    phoneLocal = profileString("phone_local"),
    phoneE164 = profileString("phone_e164"),
    phone = profileString("phone"),
    legacyPhone = profileString("telefono"),
    avatarUrl = profileString("avatar_url"),
    legacyAvatar = profileString("avatar"),
    secretQuestion = profileString("secret_question"),
)

private fun JsonObject.profileString(key: String): String? =
    get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
