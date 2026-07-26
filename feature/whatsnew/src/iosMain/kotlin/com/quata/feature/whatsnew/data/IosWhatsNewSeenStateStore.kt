package com.quata.feature.whatsnew.data

import com.quata.feature.whatsnew.domain.UserReleaseState
import platform.Foundation.NSUserDefaults

/** UserDefaults adapter for non-sensitive, device-local release progress. */
class IosWhatsNewSeenStateStore(
    private val defaults: NSUserDefaults,
    private val key: String,
) : WhatsNewSeenStateStore {
    override suspend fun read(): Result<UserReleaseState> = runCatching {
        val encoded = defaults.stringForKey(key) ?: return@runCatching UserReleaseState(null, null)
        val parts = encoded.split('|')
        require(parts.size == 3 && parts[0] == StateSchema) { "whats_new_state_invalid" }
        UserReleaseState(
            lastSeenVersionCode = parts[1].takeIf(String::isNotEmpty)?.toLongOrNull()
                ?: parts[1].takeIf(String::isNotEmpty)?.let { error("whats_new_last_seen_invalid") },
            initializedAtVersionCode = parts[2].takeIf(String::isNotEmpty)?.toLongOrNull()
                ?: parts[2].takeIf(String::isNotEmpty)?.let { error("whats_new_initialized_invalid") },
        )
    }

    override suspend fun write(state: UserReleaseState): Result<Unit> = runCatching {
        val encoded = listOf(
            StateSchema,
            state.lastSeenVersionCode?.toString().orEmpty(),
            state.initializedAtVersionCode?.toString().orEmpty(),
        ).joinToString("|")
        defaults.setObject(encoded, forKey = key)
    }

    companion object {
        const val DefaultKey = "quata.whatsnew.ios.state.v1"
        private const val StateSchema = "v1"
    }
}
