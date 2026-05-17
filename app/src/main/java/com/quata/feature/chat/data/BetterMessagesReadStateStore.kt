package com.quata.feature.chat.data

import android.content.Context
import com.quata.bettermessages.BetterMessagesJson
import com.quata.bettermessages.BmMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal class BetterMessagesReadStateStore(
    private val appContext: Context,
    private val json: Json = BetterMessagesJson.default
) {
    private val mutex = Mutex()

    suspend fun unreadCount(
        profileId: String,
        threadId: Int,
        messages: List<BmMessage>,
        currentWpUserId: Int?
    ): Int = mergeObservedMessages(profileId, threadId, messages, currentWpUserId).unreadCount

    suspend fun readMessageIds(
        profileId: String,
        threadId: Int,
        messages: List<BmMessage>,
        currentWpUserId: Int?
    ): Set<Int> = mergeObservedMessages(profileId, threadId, messages, currentWpUserId).readMessageIds

    suspend fun markThreadRead(
        profileId: String,
        threadId: Int,
        messages: List<BmMessage> = emptyList()
    ) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                val current = state.threads[threadId.toString()] ?: StoredThreadReadState()
                val messageIds = messages.map { it.messageId }.filter { it > 0 }
                val latestMessageMillis = messages.maxOfOrNull { it.created_at.toEpochMillisFromBetterMessagesOrNull() ?: 0L } ?: 0L
                val updated = current.copy(
                    readMessageIds = (current.readMessageIds + messageIds).distinct(),
                    readUntilMillis = maxOf(current.readUntilMillis, latestMessageMillis, System.currentTimeMillis())
                )
                writeState(profileId, state.copy(threads = state.threads + (threadId.toString() to updated)))
            }
        }
    }

    private suspend fun mergeObservedMessages(
        profileId: String,
        threadId: Int,
        messages: List<BmMessage>,
        currentWpUserId: Int?
    ): ObservedReadState = mutex.withLock {
        withContext(Dispatchers.IO) {
            val state = readState(profileId)
            val threadKey = threadId.toString()
            val current = state.threads[threadKey]
            val knownIds = messages.map { it.messageId }.filter { it > 0 }
            val latestMessageMillis = messages.maxOfOrNull { it.created_at.toEpochMillisFromBetterMessagesOrNull() ?: 0L } ?: 0L

            val baseline = current ?: StoredThreadReadState(
                readMessageIds = knownIds.distinct(),
                readUntilMillis = maxOf(latestMessageMillis, System.currentTimeMillis())
            )

            val ownMessageIds = messages
                .filter { currentWpUserId != null && it.senderId == currentWpUserId }
                .map { it.messageId }
                .filter { it > 0 }
            val readUntilMessageIds = messages
                .filter { message ->
                    message.created_at.toEpochMillisFromBetterMessagesOrNull()
                        ?.let { it <= baseline.readUntilMillis } == true
                }
                .map { it.messageId }
                .filter { it > 0 }
            val readMessageIds = (baseline.readMessageIds + ownMessageIds + readUntilMessageIds).distinct()
            val updated = baseline.copy(readMessageIds = readMessageIds)
            if (updated != current) {
                writeState(profileId, state.copy(threads = state.threads + (threadKey to updated)))
            }

            val readIds = readMessageIds.toSet()
            val unread = messages.count { message ->
                val messageMillis = message.created_at.toEpochMillisFromBetterMessagesOrNull() ?: Long.MAX_VALUE
                message.messageId > 0 &&
                    message.senderId != currentWpUserId &&
                    message.messageId !in readIds &&
                    messageMillis > updated.readUntilMillis
            }
            ObservedReadState(readMessageIds = readIds, unreadCount = unread)
        }
    }

    private fun readState(profileId: String): StoredReadState {
        val file = stateFile(profileId)
        if (!file.exists()) return StoredReadState()
        return runCatching { json.decodeFromString<StoredReadState>(file.readText()) }
            .getOrDefault(StoredReadState())
    }

    private fun writeState(profileId: String, state: StoredReadState) {
        val file = stateFile(profileId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    private fun stateFile(profileId: String): File {
        val safeProfileId = profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(appContext.filesDir, DIRECTORY_NAME), "$safeProfileId.json")
    }

    @Serializable
    private data class StoredReadState(
        val threads: Map<String, StoredThreadReadState> = emptyMap()
    )

    @Serializable
    private data class StoredThreadReadState(
        val readMessageIds: List<Int> = emptyList(),
        val readUntilMillis: Long = 0L
    )

    private data class ObservedReadState(
        val readMessageIds: Set<Int>,
        val unreadCount: Int
    )

    private companion object {
        const val DIRECTORY_NAME = "better_messages_read_state"
    }
}
