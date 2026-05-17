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

internal class BetterMessagesConversationTypeStore(
    private val appContext: Context,
    private val json: Json = BetterMessagesJson.default
) {
    private val mutex = Mutex()

    suspend fun markGroup(profileId: String, threadId: Int) {
        if (threadId <= 0) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                val updatedThreads = state.groupThreads + (threadId.toString() to System.currentTimeMillis())
                writeState(profileId, state.copy(groupThreads = updatedThreads))
            }
        }
    }

    suspend fun isGroup(profileId: String, threadId: Int): Boolean {
        if (threadId <= 0) return false
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                readState(profileId).groupThreads.containsKey(threadId.toString())
            }
        }
    }

    private fun readState(profileId: String): StoredConversationTypes {
        val file = stateFile(profileId)
        if (!file.exists()) return StoredConversationTypes()
        return runCatching { json.decodeFromString<StoredConversationTypes>(file.readText()) }
            .getOrDefault(StoredConversationTypes())
    }

    private fun writeState(profileId: String, state: StoredConversationTypes) {
        val file = stateFile(profileId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    private fun stateFile(profileId: String): File {
        val safeProfileId = profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(appContext.filesDir, DIRECTORY_NAME), "$safeProfileId.json")
    }

    @Serializable
    private data class StoredConversationTypes(
        val groupThreads: Map<String, Long> = emptyMap()
    )

    private companion object {
        const val DIRECTORY_NAME = "better_messages_conversation_types"
    }
}
