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

internal class BetterMessagesIdentityStore(
    private val appContext: Context,
    private val json: Json = BetterMessagesJson.default
) {
    private val mutex = Mutex()

    suspend fun wpUserId(profileId: String): Int? = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState().mappings[profileId]?.wpUserId?.takeIf { it > 0 }
        }
    }

    suspend fun upsert(profileId: String, wpUserId: Int) {
        if (profileId.isBlank() || wpUserId <= 0) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val current = readState()
                writeState(
                    current.copy(
                        mappings = current.mappings + (profileId to StoredIdentityMapping(
                            wpUserId = wpUserId,
                            updatedAt = now
                        ))
                    )
                )
            }
        }
    }

    private fun readState(): StoredIdentityState {
        val file = stateFile()
        if (!file.exists()) return StoredIdentityState()
        return runCatching { json.decodeFromString<StoredIdentityState>(file.readText()) }
            .getOrDefault(StoredIdentityState())
    }

    private fun writeState(state: StoredIdentityState) {
        val file = stateFile()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    private fun stateFile(): File =
        File(File(appContext.filesDir, DIRECTORY_NAME), FILE_NAME)

    @Serializable
    private data class StoredIdentityState(
        val mappings: Map<String, StoredIdentityMapping> = emptyMap()
    )

    @Serializable
    private data class StoredIdentityMapping(
        val wpUserId: Int,
        val updatedAt: Long = 0L
    )

    private companion object {
        const val DIRECTORY_NAME = "better_messages_identity"
        const val FILE_NAME = "profile_wp_users.json"
    }
}
