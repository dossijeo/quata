package com.quata.core.moderation

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.withTimeoutOrNull

interface UgcTermsGateway {
    suspend fun hasAcceptedTerms(version: String = CurrentUgcTermsVersion): Result<Boolean>
    suspend fun acceptTerms(version: String = CurrentUgcTermsVersion): Result<Unit>
}

fun interface UgcTermsRemoteGateway {
    suspend fun hasAcceptedTerms(profileId: String, version: String): Result<Boolean>
}

fun interface UgcTermsAcceptanceGateway {
    suspend fun acceptTerms(profileId: String, version: String): Result<Unit>
}

class PreferenceUgcTermsAcceptanceStore(
    private val preferences: PreferenceStore,
) {
    suspend fun isAccepted(profileId: String, version: String): Boolean =
        preferences.getString(acceptedKey(profileId, version)) == "true"

    suspend fun isPending(profileId: String, version: String): Boolean =
        preferences.getString(pendingKey(profileId, version)) == "true"

    suspend fun markAcceptedPendingSync(profileId: String, version: String) {
        preferences.putString(acceptedKey(profileId, version), "true")
        preferences.putString(pendingKey(profileId, version), "true")
    }

    suspend fun markAcceptedSynced(profileId: String, version: String) {
        preferences.putString(acceptedKey(profileId, version), "true")
        preferences.remove(pendingKey(profileId, version))
    }

    suspend fun clear(profileId: String, version: String) {
        preferences.remove(acceptedKey(profileId, version))
        preferences.remove(pendingKey(profileId, version))
    }

    private fun acceptedKey(profileId: String, version: String): String =
        "ugc_terms:accepted:$profileId:$version"

    private fun pendingKey(profileId: String, version: String): String =
        "ugc_terms:pending:$profileId:$version"
}

class LocalFirstUgcTermsGateway(
    private val profileIdProvider: suspend () -> String?,
    private val store: PreferenceUgcTermsAcceptanceStore,
    private val remote: UgcTermsRemoteGateway,
    private val acceptance: UgcTermsAcceptanceGateway,
) : UgcTermsGateway {
    override suspend fun hasAcceptedTerms(version: String): Result<Boolean> = runCatching {
        val profileId = requireProfileId()
        if (store.isAccepted(profileId, version)) {
            if (store.isPending(profileId, version)) {
                syncPendingAcceptance(profileId, version)
            }
            return@runCatching true
        }
        val acceptedRemotely = remote.hasAcceptedTerms(profileId, version).getOrThrow()
        if (acceptedRemotely) store.markAcceptedSynced(profileId, version)
        acceptedRemotely
    }

    override suspend fun acceptTerms(version: String): Result<Unit> = runCatching {
        val profileId = requireProfileId()
        store.markAcceptedPendingSync(profileId, version)
        syncPendingAcceptance(profileId, version)
        Unit
    }

    private suspend fun syncPendingAcceptance(profileId: String, version: String) {
        withTimeoutOrNull(PendingSyncTimeoutMillis) {
            acceptance.acceptTerms(profileId, version).onSuccess {
                store.markAcceptedSynced(profileId, version)
            }
        }
    }

    private suspend fun requireProfileId(): String =
        profileIdProvider()?.trim()?.takeIf(String::isNotEmpty)
            ?: error("ugc_terms_session_required")

    private companion object {
        const val PendingSyncTimeoutMillis = 8_000L
    }
}

object AcceptAllUgcTermsGateway : UgcTermsGateway {
    override suspend fun hasAcceptedTerms(version: String): Result<Boolean> = Result.success(true)
    override suspend fun acceptTerms(version: String): Result<Unit> = Result.success(Unit)
}
