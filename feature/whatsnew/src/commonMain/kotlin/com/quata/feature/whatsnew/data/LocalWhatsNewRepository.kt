package com.quata.feature.whatsnew.data

import com.quata.feature.whatsnew.domain.PendingRelease
import com.quata.feature.whatsnew.domain.UserReleaseState
import com.quata.feature.whatsnew.domain.WhatsNewRepository

class LocalWhatsNewRelease(
    val releaseId: String,
    val versionCode: Long,
    val versionName: String?,
    val notes: Map<String, String>,
)

interface WhatsNewSeenStateStore {
    suspend fun read(): Result<UserReleaseState>
    suspend fun write(state: UserReleaseState): Result<Unit>
}

/**
 * Offline repository for a platform-owned, versioned release catalog.
 *
 * It has no network fallback and never claims server synchronization. Seen state is monotonic:
 * reinstall/migration code cannot accidentally move it backwards and show an older release again.
 */
class LocalWhatsNewRepository(
    releases: List<LocalWhatsNewRelease>,
    private val store: WhatsNewSeenStateStore,
) : WhatsNewRepository {
    private val catalog = releases
        .filter { it.releaseId.isNotBlank() && it.versionCode > 0 && it.notes.hasUsableNote() }
        .distinctBy(LocalWhatsNewRelease::versionCode)
        .sortedBy(LocalWhatsNewRelease::versionCode)

    override suspend fun getPendingReleases(
        installedVersionCode: Long,
        languageTags: List<String>,
    ): Result<List<PendingRelease>> = store.read().map { state ->
        catalog
            .asSequence()
            .filter { it.versionCode <= installedVersionCode }
            .filter { it.versionCode > (state.lastSeenVersionCode ?: 0L) }
            .mapNotNull { it.localized(languageTags) }
            .toList()
    }

    override suspend fun getReleaseHistory(languageTags: List<String>): Result<List<PendingRelease>> =
        Result.success(catalog.mapNotNull { it.localized(languageTags) }.sortedByDescending(PendingRelease::versionCode))

    override suspend fun initializeForNewUser(installedVersionCode: Long): Result<Unit> =
        store.read().fold(
            onSuccess = { current ->
                if (current.initializedAtVersionCode != null) {
                    Result.success(Unit)
                } else {
                    store.write(current.copy(initializedAtVersionCode = installedVersionCode.coerceAtLeast(1L)))
                }
            },
            onFailure = { Result.failure(it) },
        )

    override suspend fun markReleasesSeen(
        upToVersionCode: Long,
        installedVersionCode: Long,
    ): Result<Unit> {
        if (upToVersionCode <= 0 || installedVersionCode <= 0 || upToVersionCode > installedVersionCode) {
            return Result.failure(IllegalArgumentException("whats_new_seen_version_invalid"))
        }
        return store.read().fold(
            onSuccess = { current ->
                val nextSeen = maxOf(current.lastSeenVersionCode ?: 0L, upToVersionCode)
                store.write(
                    current.copy(
                        lastSeenVersionCode = nextSeen,
                        initializedAtVersionCode = current.initializedAtVersionCode ?: installedVersionCode,
                    ),
                )
            },
            onFailure = { Result.failure(it) },
        )
    }
}

private fun LocalWhatsNewRelease.localized(languageTags: List<String>): PendingRelease? {
    val cleanNotes = notes.mapValues { it.value.trim() }.filterValues(String::isNotEmpty)
    val note = cleanNotes.resolve(languageTags) ?: return null
    return PendingRelease(
        releaseId = releaseId,
        versionCode = versionCode,
        versionName = versionName,
        localizedNote = note,
        availableLanguageTags = cleanNotes.keys,
    )
}

private fun Map<String, String>.hasUsableNote(): Boolean = values.any { it.isNotBlank() }

/** Exact tag, language-only tag, English and finally the first stable catalog translation. */
private fun Map<String, String>.resolve(languageTags: List<String>): String? {
    languageTags.firstNotNullOfOrNull { requested ->
        entries.firstOrNull { it.key.normalizedLanguageTag() == requested.normalizedLanguageTag() }?.value
    }?.let { return it }
    languageTags.map { it.normalizedLanguageTag().substringBefore('-') }.firstNotNullOfOrNull { language ->
        entries.firstOrNull { it.key.normalizedLanguageTag().substringBefore('-') == language }?.value
    }?.let { return it }
    entries.firstOrNull { it.key.normalizedLanguageTag().substringBefore('-') == "en" }?.value?.let { return it }
    return entries.sortedBy { it.key.lowercase() }.firstOrNull()?.value
}

private fun String.normalizedLanguageTag(): String = trim().replace('_', '-').lowercase()
