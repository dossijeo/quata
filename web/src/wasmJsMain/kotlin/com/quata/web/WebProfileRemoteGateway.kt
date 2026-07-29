package com.quata.web

import com.quata.feature.profile.data.ProfileCachePolicy
import com.quata.feature.profile.data.ProfileRemoteGateway
import com.quata.feature.profile.data.ProfileRemoteRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Authenticated browser transport for Profile's portable repository.
 *
 * [WebPostgrestClient] owns the injected Web session and sends its bearer token on every read.
 * It deliberately exposes one-shot flows rather than pretending the browser has a realtime
 * subscription. A future realtime adapter can replace these flows without changing Profile's
 * common contract.
 *
 * This gateway is deliberately read-only. The deployed Web contract has not proved a safe
 * profile/SOS write path yet, so accepting a mutation here could produce an incorrect success
 * state or change data governed by legacy RLS policies. Callers receive a stable, explicit
 * failure before any PATCH/POST/DELETE request can be constructed.
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

    override suspend fun saveProfile(profileId: String, @Suppress("UNUSED_PARAMETER") patch: Map<String, String?>): Nothing =
        webProfileMutationContractUnverified(profileId)

    override suspend fun saveRecoverySecret(
        profileId: String,
        @Suppress("UNUSED_PARAMETER") secretQuestion: String,
        @Suppress("UNUSED_PARAMETER") secretAnswer: String,
    ): Nothing =
        webProfileMutationContractUnverified(profileId)

    override suspend fun saveEmergencyContacts(
        profileId: String,
        @Suppress("UNUSED_PARAMETER") contactIds: List<String>,
    ): Nothing =
        webProfileMutationContractUnverified(profileId)

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
    }
}

internal fun webProfileMutationContractUnverified(profileId: String): Nothing {
    requireProfileId(profileId)
    throw IllegalStateException("web_profile_mutation_contract_unverified")
}

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
