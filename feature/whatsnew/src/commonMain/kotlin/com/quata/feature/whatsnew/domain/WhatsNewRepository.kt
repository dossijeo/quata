package com.quata.feature.whatsnew.domain

interface WhatsNewRepository {
    suspend fun getPendingReleases(
        installedVersionCode: Long,
        languageTags: List<String>
    ): Result<List<PendingRelease>>

    suspend fun getReleaseHistory(languageTags: List<String>): Result<List<PendingRelease>>

    suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit>

    suspend fun markReleasesSeen(
        upToVersionCode: Long,
        installedVersionCode: Long
    ): Result<Unit>
}
