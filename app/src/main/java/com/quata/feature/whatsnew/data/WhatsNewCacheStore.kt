package com.quata.feature.whatsnew.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class WhatsNewCacheEntry(
    val lastSeenVersionCode: Long? = null,
    val pendingSeenVersionCode: Long? = null,
    val pendingSeenInstalledCode: Long? = null,
    val releases: List<CachedRelease> = emptyList()
)

@Serializable
internal data class CachedRelease(
    val releaseId: String,
    val versionCode: Long,
    val versionName: String? = null,
    val notes: Map<String, String> = emptyMap()
)

internal class WhatsNewCacheStore(context: Context) {
    private val preferences = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun read(profileId: String): WhatsNewCacheEntry =
        preferences.getString(key(profileId), null)
            ?.let { raw -> runCatching { json.decodeFromString<WhatsNewCacheEntry>(raw) }.getOrNull() }
            ?: WhatsNewCacheEntry()

    fun write(profileId: String, entry: WhatsNewCacheEntry) {
        preferences.edit().putString(key(profileId), json.encodeToString(entry)).apply()
    }

    fun cacheReleases(profileId: String, releases: List<CachedRelease>) {
        val current = read(profileId)
        write(profileId, current.copy(releases = releases))
    }

    fun markSeenLocally(profileId: String, versionCode: Long, installedVersionCode: Long) {
        val current = read(profileId)
        write(
            profileId,
            current.copy(
                lastSeenVersionCode = maxOf(current.lastSeenVersionCode ?: 0L, versionCode),
                pendingSeenVersionCode = maxOf(current.pendingSeenVersionCode ?: 0L, versionCode),
                pendingSeenInstalledCode = maxOf(current.pendingSeenInstalledCode ?: 0L, installedVersionCode)
            )
        )
    }

    fun markProgressSynced(profileId: String, versionCode: Long) {
        val current = read(profileId)
        write(
            profileId,
            current.copy(
                lastSeenVersionCode = maxOf(current.lastSeenVersionCode ?: 0L, versionCode),
                pendingSeenVersionCode = null,
                pendingSeenInstalledCode = null
            )
        )
    }

    private fun key(profileId: String) = "state_$profileId"

    private companion object {
        const val PrefsName = "quata_whats_new_cache"
    }
}
