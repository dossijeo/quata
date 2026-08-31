package com.quata.web

import com.quata.core.moderation.LocalFirstUgcTermsGateway
import com.quata.core.moderation.PreferenceUgcTermsAcceptanceStore
import com.quata.core.moderation.UgcTermsAcceptanceGateway
import com.quata.core.moderation.UgcTermsGateway
import com.quata.core.moderation.UgcTermsRemoteGateway
import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun webUgcTermsGateway(
    authRepository: WebAuthRepository,
    rpcClient: WebPostgrestRpcClient,
    preferences: PreferenceStore,
): UgcTermsGateway {
    val pendingSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return LocalFirstUgcTermsGateway(
        profileIdProvider = { authRepository.sessionForAuthenticatedRequest()?.userId },
        store = PreferenceUgcTermsAcceptanceStore(preferences),
        remote = UgcTermsRemoteGateway { profileId, version ->
            rpcClient.post(
                functionName = "quata_has_accepted_ugc_terms",
                body = ugcTermsBody(profileId, version),
            ).mapWebUgcTermsBoolean()
        },
        acceptance = UgcTermsAcceptanceGateway { profileId, version ->
            when (val result = rpcClient.post("quata_accept_ugc_terms", ugcTermsBody(profileId, version))) {
                is WebPostgrestResult.Success -> Result.success(Unit)
                is WebPostgrestResult.Failure -> Result.failure(IllegalStateException(result.reason))
            }
        },
        pendingSyncLauncher = { task ->
            pendingSyncScope.launch {
                task()
            }
        },
    )
}

private fun ugcTermsBody(profileId: String, version: String): String = buildJsonObject {
    put("p_actor_profile_id", profileId)
    put("p_terms_version", version)
}.toString()

private fun WebPostgrestResult.mapWebUgcTermsBoolean(): Result<Boolean> = when (this) {
    is WebPostgrestResult.Success -> Result.success(
        Json.parseToJsonElement(body).jsonPrimitive.booleanOrNull ?: false,
    )
    is WebPostgrestResult.Failure -> Result.failure(IllegalStateException(reason))
}
