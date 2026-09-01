package com.quata.core.moderation

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface UgcTermsGateway {
    suspend fun hasAcceptedTerms(version: String = CurrentUgcTermsVersion): Result<Boolean>
    suspend fun acceptTerms(version: String = CurrentUgcTermsVersion): Result<Unit>
}

interface UgcTermsPendingSyncGateway {
    suspend fun flushPendingAcceptance(version: String = CurrentUgcTermsVersion): Result<Boolean>
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
    private val pendingSyncLauncher: (((suspend () -> Unit) -> Unit))? = null,
) : UgcTermsGateway, UgcTermsPendingSyncGateway {
    private val flushMutex = Mutex()

    override suspend fun hasAcceptedTerms(version: String): Result<Boolean> = runCatching {
        val profileId = requireProfileId()
        if (store.isAccepted(profileId, version)) {
            launchPendingSync(version)
            return@runCatching true
        }
        val acceptedRemotely = remote.hasAcceptedTerms(profileId, version).getOrThrow()
        if (acceptedRemotely) store.markAcceptedSynced(profileId, version)
        acceptedRemotely
    }

    override suspend fun acceptTerms(version: String): Result<Unit> = runCatching {
        val profileId = requireProfileId()
        store.markAcceptedPendingSync(profileId, version)
        launchPendingSync(version)
    }

    override suspend fun flushPendingAcceptance(version: String): Result<Boolean> = runCatching {
        flushMutex.withLock {
            val profileId = requireProfileId()
            if (!store.isPending(profileId, version)) return@withLock true
            acceptance.acceptTerms(profileId, version).getOrThrow()
            store.markAcceptedSynced(profileId, version)
            true
        }
    }

    suspend fun hasPendingAcceptance(version: String = CurrentUgcTermsVersion): Result<Boolean> = runCatching {
        val profileId = requireProfileId()
        store.isPending(profileId, version)
    }

    private suspend fun requireProfileId(): String =
        profileIdProvider()?.trim()?.takeIf(String::isNotEmpty)
            ?: error("ugc_terms_session_required")

    private fun launchPendingSync(version: String) {
        pendingSyncLauncher?.invoke {
            flushPendingAcceptanceWithBackoff(version)
        }
    }

    private suspend fun flushPendingAcceptanceWithBackoff(version: String) {
        var delayMillis = PendingSyncInitialDelayMillis
        repeat(PendingSyncRetryAttempts) { attempt ->
            if (flushPendingAcceptance(version).getOrDefault(false)) return
            if (attempt != PendingSyncRetryAttempts - 1) {
                delay(delayMillis)
                delayMillis = (delayMillis * 2).coerceAtMost(PendingSyncMaxDelayMillis)
            }
        }
    }

    private companion object {
        const val PendingSyncRetryAttempts = 5
        const val PendingSyncInitialDelayMillis = 1_000L
        const val PendingSyncMaxDelayMillis = 30_000L
    }
}

suspend fun UgcTermsGateway.flushPendingAcceptanceIfSupported(
    version: String = CurrentUgcTermsVersion,
): Result<Boolean> =
    if (this is UgcTermsPendingSyncGateway) {
        flushPendingAcceptance(version)
    } else {
        Result.success(false)
    }

object AcceptAllUgcTermsGateway : UgcTermsGateway {
    override suspend fun hasAcceptedTerms(version: String): Result<Boolean> = Result.success(true)
    override suspend fun acceptTerms(version: String): Result<Unit> = Result.success(Unit)
}
