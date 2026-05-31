package com.quata.feature.chat.data

import android.content.Context
import com.quata.bettermessages.BetterMessagesJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal class BetterMessagesPollingStateStore(
    private val appContext: Context,
    private val json: Json = BetterMessagesJson.default
) {
    private val mutex = Mutex()

    suspend fun lastInboxUpdate(profileId: String): Long = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState(profileId).lastInboxUpdate
        }
    }

    suspend fun updateLastInboxUpdate(profileId: String, value: Long) {
        if (value <= 0L) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val current = readState(profileId)
                if (value > current.lastInboxUpdate) {
                    writeState(profileId, current.copy(lastInboxUpdate = value))
                }
            }
        }
    }

    private fun readState(profileId: String): StoredPollingState {
        val file = stateFile(profileId)
        if (!file.exists()) return StoredPollingState()
        return runCatching { json.decodeFromString<StoredPollingState>(file.readText()) }
            .getOrDefault(StoredPollingState())
    }

    private fun writeState(profileId: String, state: StoredPollingState) {
        val file = stateFile(profileId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    private fun stateFile(profileId: String): File {
        val safeProfileId = profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(appContext.filesDir, DIRECTORY_NAME), "$safeProfileId.json")
    }

    @Serializable
    private data class StoredPollingState(
        val lastInboxUpdate: Long = 0L
    )

    private companion object {
        const val DIRECTORY_NAME = "better_messages_polling_state"
    }
}
