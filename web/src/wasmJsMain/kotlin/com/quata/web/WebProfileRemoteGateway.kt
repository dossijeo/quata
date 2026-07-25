package com.quata.web

import com.quata.feature.profile.data.ProfileCachePolicy
import com.quata.feature.profile.data.ProfileRemoteGateway
import com.quata.feature.profile.data.ProfileRemoteRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
 * Mutations use the same authenticated browser session as reads and require PostgREST to return
 * the affected rows. RLS/HTTP failures remain errors: callers must never turn a failed remote
 * mutation into a local-success notification.
 */
class WebProfileRemoteGateway(
    private val client: WebPostgrestClient,
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
        requireProfileId(profileId)
        val body = patch.toJsonObject().toString()
        val rows = client.patch(
            table = ProfilesTable,
            query = mapOf("id" to "eq.$profileId"),
            body = body,
        ).requireRows("web_profile_patch")
        check(rows.singleOrNull()?.profileString("id") == profileId) {
            "web_profile_patch_unconfirmed"
        }
    }

    override suspend fun saveEmergencyContacts(profileId: String, contactIds: List<String>) {
        requireProfileId(profileId)
        val normalized = contactIds.map(String::trim).filter(String::isNotBlank).distinct().take(MaxEmergencyContacts)
        normalized.forEach(::requireProfileId)
        client.delete(EmergencyContactsTable, mapOf("profile_id" to "eq.$profileId"))
            .requireSuccess("web_profile_emergency_contacts_delete")
        if (normalized.isEmpty()) return
        val body = JsonArray(normalized.mapIndexed { index, contactId ->
            buildJsonObject {
                put("profile_id", profileId)
                put("contact_profile_id", contactId)
                put("position", index + 1)
            }
        }).toString()
        val rows = client.post(EmergencyContactsTable, body)
            .requireRows("web_profile_emergency_contacts_insert")
        val saved = rows.mapNotNull { it.profileString("contact_profile_id") }
        check(saved == normalized) { "web_profile_emergency_contacts_unconfirmed" }
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

    private fun WebPostgrestResult.requireSuccess(operation: String): WebPostgrestResult.Success = when (this) {
        is WebPostgrestResult.Success -> this
        is WebPostgrestResult.Failure -> throw WebProfileRemoteMutationException(operation, this)
    }

    private fun WebPostgrestResult.requireRows(operation: String): List<JsonObject> {
        val response = requireSuccess(operation)
        return Json.parseToJsonElement(response.body).jsonArray.map { it.jsonObject }
    }

    private companion object {
        const val ProfilesTable = "community_profiles"
        const val EmergencyContactsTable = "community_emergency_contacts"
        const val ProfileDirectoryLimit = 500
        const val MaxEmergencyContacts = 5
        const val ProfileSelect =
            "id,display_name,nombre,neighborhood,barrio,country_code,code,phone_local,phone_e164,phone,telefono,avatar_url,avatar,secret_question"
    }
}

class WebProfileRemoteMutationException(
    operation: String,
    failure: WebPostgrestResult.Failure,
) : IllegalStateException("$operation:${failure.kind.name.lowercase()}:${failure.reason}")

private val WebProfileIdentifier = Regex("[A-Za-z0-9_-]+")

private fun Collection<String>.toPostgrestInFilter(): String =
    "in.(${distinct().joinToString(",") { it.requireProfileIdentifier() }})"

private fun requireProfileId(value: String) {
    value.requireProfileIdentifier()
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

internal fun Map<String, String?>.toJsonObject(): JsonObject = buildJsonObject {
    this@toJsonObject.forEach { (key, value) ->
        if (value == null) put(key, JsonNull) else put(key, value)
    }
}
