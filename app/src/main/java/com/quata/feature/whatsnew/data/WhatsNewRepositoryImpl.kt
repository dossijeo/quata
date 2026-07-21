package com.quata.feature.whatsnew.data

import android.content.Context
import android.os.LocaleList
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quata.QuataApp
import com.quata.core.session.SessionManager
import com.quata.data.supabase.AndroidPendingReleaseDto
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class WhatsNewRepositoryImpl internal constructor(
    private val appContext: Context,
    private val api: SupabaseCommunityApi,
    private val sessionManager: SessionManager,
    private val cacheStore: WhatsNewCacheStore = WhatsNewCacheStore(appContext)
) : WhatsNewRepository {

    override suspend fun getPendingReleases(
        installedVersionCode: Long,
        locales: LocaleList
    ): Result<List<PendingRelease>> = withContext(Dispatchers.IO) {
        val profileId = sessionManager.currentSession()?.userId
            ?: return@withContext Result.failure(IllegalStateException("authentication_required"))
        syncPendingProgress(profileId)

        runCatching {
            val remoteReleases = api.getPendingAndroidReleases(installedVersionCode)
            cacheStore.cacheReleases(
                profileId,
                remoteReleases.map { dto ->
                    CachedRelease(
                        releaseId = dto.release_id,
                        versionCode = dto.version_code,
                        versionName = dto.version_name,
                        notes = dto.notes
                    )
                }
            )
            remoteReleases
                .mapNotNull { dto -> dto.toPendingRelease(locales) }
                .distinctBy { it.versionCode }
                .sortedBy { it.versionCode }
        }.recoverCatching {
            val cached = cacheStore.read(profileId)
            cached.releases
                .filter { release ->
                    release.versionCode <= installedVersionCode &&
                        release.versionCode > (cached.lastSeenVersionCode ?: 0L)
                }
                .mapNotNull { cachedRelease ->
                    cachedRelease.toPendingRelease(locales)
                }
                .distinctBy { it.versionCode }
                .sortedBy { it.versionCode }
        }
    }

    override suspend fun getReleaseHistory(locales: LocaleList): Result<List<PendingRelease>> =
        withContext(Dispatchers.IO) {
            val profileId = sessionManager.currentSession()?.userId
                ?: return@withContext Result.failure(IllegalStateException("authentication_required"))
            runCatching {
                api.getAndroidReleaseHistory()
                    .also { releases ->
                        cacheStore.cacheReleases(
                            profileId,
                            releases.map { dto ->
                                CachedRelease(
                                    releaseId = dto.release_id,
                                    versionCode = dto.version_code,
                                    versionName = dto.version_name,
                                    notes = dto.notes
                                )
                            }
                        )
                    }
                    .mapNotNull { dto -> dto.toPendingRelease(locales) }
                    .distinctBy { it.versionCode }
                    .sortedByDescending { it.versionCode }
            }.recoverCatching {
                cacheStore.read(profileId).releases
                    .mapNotNull { cachedRelease -> cachedRelease.toPendingRelease(locales) }
                    .distinctBy { it.versionCode }
                    .sortedByDescending { it.versionCode }
            }
        }

    override suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit> =
        getPendingReleases(installedVersionCode, LocaleList.getDefault()).map { Unit }

    override suspend fun markReleasesSeen(
        upToVersionCode: Long,
        installedVersionCode: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val profileId = sessionManager.currentSession()?.userId
            ?: return@withContext Result.failure(IllegalStateException("authentication_required"))
        cacheStore.markSeenLocally(profileId, upToVersionCode, installedVersionCode)
        val remoteResult = runCatching {
            api.markAndroidReleasesSeen(upToVersionCode, installedVersionCode)
            cacheStore.markProgressSynced(profileId, upToVersionCode)
        }
        if (remoteResult.isFailure) {
            enqueuePendingSync(appContext)
        }
        Result.success(Unit)
    }

    internal suspend fun syncPendingProgress(): Boolean {
        val profileId = sessionManager.currentSession()?.userId ?: return false
        return syncPendingProgress(profileId)
    }

    private suspend fun syncPendingProgress(profileId: String): Boolean {
        val cached = cacheStore.read(profileId)
        val pendingCode = cached.pendingSeenVersionCode ?: return true
        val installedCode = cached.pendingSeenInstalledCode ?: pendingCode
        return runCatching {
            api.markAndroidReleasesSeen(pendingCode, installedCode)
            cacheStore.markProgressSynced(profileId, pendingCode)
            true
        }.getOrDefault(false)
    }

    private fun AndroidPendingReleaseDto.toPendingRelease(locales: LocaleList): PendingRelease? {
        val localizedNote = resolveReleaseNote(notes, locales) ?: return null
        return PendingRelease(
            releaseId = release_id,
            versionCode = version_code,
            versionName = version_name,
            localizedNote = localizedNote,
            availableLanguageTags = available_language_tags.toSet()
        )
    }

    private fun CachedRelease.toPendingRelease(locales: LocaleList): PendingRelease? {
        val localizedNote = resolveReleaseNote(notes, locales) ?: return null
        return PendingRelease(
            releaseId = releaseId,
            versionCode = versionCode,
            versionName = versionName,
            localizedNote = localizedNote,
            availableLanguageTags = notes.keys
        )
    }
}

/** Exact tag, language-only tag, English and finally any usable translation. */
fun resolveReleaseNote(notes: Map<String, String>, locales: LocaleList): String? {
    val cleanNotes = notes
        .mapValues { (_, value) -> value.trim() }
        .filterValues { it.isNotEmpty() }
    if (cleanNotes.isEmpty()) return null

    for (index in 0 until locales.size()) {
        val locale = locales[index]
        cleanNotes.entries.firstOrNull { (tag, _) -> tag.equals(locale.toLanguageTag(), ignoreCase = true) }
            ?.value
            ?.let { return it }
    }
    for (index in 0 until locales.size()) {
        val locale = locales[index]
        cleanNotes.entries.firstOrNull { (tag, _) ->
            Locale.forLanguageTag(tag).language.equals(locale.language, ignoreCase = true)
        }?.value?.let { return it }
    }
    cleanNotes.entries.firstOrNull { (tag, _) -> Locale.forLanguageTag(tag).language.equals("en", ignoreCase = true) }
        ?.value
        ?.let { return it }
    return cleanNotes.toSortedMap(String.CASE_INSENSITIVE_ORDER).values.firstOrNull()
}

class WhatsNewSeenSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as QuataApp).container.whatsNewRepository
        return if (repository.syncPendingProgress()) Result.success() else Result.retry()
    }
}

private fun enqueuePendingSync(context: Context) {
    val request = OneTimeWorkRequestBuilder<WhatsNewSeenSyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "quata_whats_new_seen_sync",
        ExistingWorkPolicy.KEEP,
        request
    )
}
