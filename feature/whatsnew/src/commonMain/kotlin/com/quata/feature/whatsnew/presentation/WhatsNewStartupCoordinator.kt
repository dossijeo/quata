package com.quata.feature.whatsnew.presentation

import com.quata.feature.whatsnew.domain.WhatsNewRepository

enum class WhatsNewStartupDecision { Skip, Show }

interface WhatsNewStartupAcknowledgementStore {
    suspend fun readAcknowledgedVersionCode(): Result<Long?>
    suspend fun writeAcknowledgedVersionCode(versionCode: Long): Result<Unit>
}

/** Evaluates the local catalog once per installed version without blocking public navigation. */
class WhatsNewStartupCoordinator(
    private val repository: WhatsNewRepository,
    private val acknowledgementStore: WhatsNewStartupAcknowledgementStore,
) {
    suspend fun evaluate(installedVersionCode: Long, languageTags: List<String>): Result<WhatsNewStartupDecision> {
        if (installedVersionCode <= 0) return Result.failure(IllegalArgumentException("whats_new_version_invalid"))
        val acknowledged = acknowledgementStore.readAcknowledgedVersionCode().getOrElse { return Result.failure(it) }
        if ((acknowledged ?: 0L) >= installedVersionCode) return Result.success(WhatsNewStartupDecision.Skip)
        repository.initializeForNewUser(installedVersionCode).getOrElse { return Result.failure(it) }
        val pending = repository.getPendingReleases(installedVersionCode, languageTags).getOrElse { return Result.failure(it) }
        if (pending.isNotEmpty()) return Result.success(WhatsNewStartupDecision.Show)
        return acknowledge(installedVersionCode).map { WhatsNewStartupDecision.Skip }
    }

    suspend fun acknowledge(installedVersionCode: Long): Result<Unit> {
        if (installedVersionCode <= 0) return Result.failure(IllegalArgumentException("whats_new_version_invalid"))
        val current = acknowledgementStore.readAcknowledgedVersionCode().getOrElse { return Result.failure(it) }
        return acknowledgementStore.writeAcknowledgedVersionCode(maxOf(current ?: 0L, installedVersionCode))
    }
}
