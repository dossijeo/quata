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

internal class BetterMessagesAbandonedConversationStore(
    private val appContext: Context,
    private val json: Json = BetterMessagesJson.default
) {
    private val mutex = Mutex()

    suspend fun markAbandoned(profileId: String, threadId: Int) {
        if (threadId <= 0) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                val updatedThreads = state.threads + (threadId.toString() to System.currentTimeMillis())
                writeState(profileId, state.copy(threads = updatedThreads))
            }
        }
    }

    suspend fun clearAbandoned(profileId: String, threadId: Int) {
        if (threadId <= 0) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                val key = threadId.toString()
                if (key !in state.threads) return@withContext
                writeState(profileId, state.copy(threads = state.threads - key))
            }
        }
    }

    suspend fun isAbandoned(profileId: String, threadId: Int): Boolean {
        if (threadId <= 0) return false
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                readState(profileId).threads.containsKey(threadId.toString())
            }
        }
    }

    suspend fun abandonedThreadIds(profileId: String): Set<Int> {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                readState(profileId).threads.keys.mapNotNull { it.toIntOrNull() }.toSet()
            }
        }
    }

    private fun readState(profileId: String): StoredAbandonedConversations {
        val file = stateFile(profileId)
        if (!file.exists()) return StoredAbandonedConversations()
        return runCatching { json.decodeFromString<StoredAbandonedConversations>(file.readText()) }
            .getOrDefault(StoredAbandonedConversations())
    }

    private fun writeState(profileId: String, state: StoredAbandonedConversations) {
        val file = stateFile(profileId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    private fun stateFile(profileId: String): File {
        val safeProfileId = profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(appContext.filesDir, DIRECTORY_NAME), "$safeProfileId.json")
    }

    @Serializable
    private data class StoredAbandonedConversations(
        val threads: Map<String, Long> = emptyMap()
    )

    private companion object {
        const val DIRECTORY_NAME = "better_messages_abandoned_conversations"
    }
}
