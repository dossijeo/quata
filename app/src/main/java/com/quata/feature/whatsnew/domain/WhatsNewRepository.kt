package com.quata.feature.whatsnew.domain

import android.os.LocaleList

interface WhatsNewRepository {
    suspend fun getPendingReleases(
        installedVersionCode: Long,
        locales: LocaleList
    ): Result<List<PendingRelease>>

    suspend fun getReleaseHistory(locales: LocaleList): Result<List<PendingRelease>>

    suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit>

    suspend fun markReleasesSeen(
        upToVersionCode: Long,
        installedVersionCode: Long
    ): Result<Unit>
}
