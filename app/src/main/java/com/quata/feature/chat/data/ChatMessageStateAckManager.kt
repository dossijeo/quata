package com.quata.feature.chat.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quata.QuataApp
import com.quata.core.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

enum class ChatMessageStateAckStatus(val wireValue: String) {
    Delivered("DELIVERED"),
    Read("READ")
}

class ChatMessageStateAckManager(
    private val appContext: Context,
    private val remote: ChatRemoteDataSource,
    private val sessionManager: SessionManager,
    private val store: ChatMessageStateAckStore = ChatMessageStateAckStore(appContext)
) {
    suspend fun markMessages(
        messageIds: List<Long>,
        status: ChatMessageStateAckStatus,
        source: String
    ): Boolean {
        val session = sessionManager.currentSession() ?: return false
        val cleanMessageIds = messageIds.distinct().filter { it > 0L }
        if (cleanMessageIds.isEmpty()) return true
        val handledIds = store.alreadyHandledMessageIds(session.userId, cleanMessageIds, status)
        val pendingAcks = cleanMessageIds
            .filterNot { it in handledIds }
            .map { messageId ->
                ChatMessageStateAck(
                    profileId = session.userId,
                    messageId = messageId,
                    status = status.wireValue,
                    source = source
                )
            }
        if (pendingAcks.isEmpty()) return true
        return try {
            remote.markChatMessagesState(
                profileId = session.userId,
                messageIds = pendingAcks.map { it.messageId },
                status = status.wireValue,
                source = source
            )
            pendingAcks.forEach { store.markSent(it) }
            true
        } catch (error: Throwable) {
            Log.w(TAG, "Could not mark chat message state=${status.wireValue}; queueing", error)
            pendingAcks.forEach { store.enqueue(it) }
            ChatMessageStateWorkScheduler.scheduleOneTime(appContext)
            false
        }
    }

    suspend fun flushPending(): Boolean {
        val session = sessionManager.currentSession() ?: return true
        val pending = store.pendingAcks(session.userId)
        if (pending.isEmpty()) return true
        var allSent = true
        pending
            .groupBy { Triple(it.profileId, it.status, it.source.takeIf(String::isNotBlank) ?: "worker") }
            .forEach { (key, acks) ->
                val (profileId, status, source) = key
                val messageIds = acks.map { it.messageId }.distinct()
                try {
                    remote.markChatMessagesState(profileId, messageIds, status, source)
                    acks.forEach { store.markSent(it) }
                } catch (error: Throwable) {
                    allSent = false
                    Log.w(TAG, "Could not flush chat message state=$status profile=$profileId", error)
                }
            }
        return allSent
    }

    companion object {
        private const val TAG = "ChatMessageStateAck"
    }
}

class ChatMessageStateSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? QuataApp ?: return Result.retry()
        val manager = ChatMessageStateAckManager(
            appContext = applicationContext,
            remote = ChatRemoteDataSource(app.container.supabaseCommunityApi),
            sessionManager = app.container.sessionManager
        )
        return if (manager.flushPending()) Result.success() else Result.retry()
    }
}

object ChatMessageStateWorkScheduler {
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleOneTime(context: Context) {
        val request = OneTimeWorkRequestBuilder<ChatMessageStateSyncWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ChatMessageStateSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private const val ONE_TIME_WORK_NAME = "quata-chat-message-state-sync"
    private const val PERIODIC_WORK_NAME = "quata-chat-message-state-sync-periodic"
}

class ChatMessageStateAckStore(
    private val appContext: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
) {
    private val mutex = Mutex()

    suspend fun alreadyHandledMessageIds(profileId: String, messageIds: List<Long>, status: ChatMessageStateAckStatus): Set<Long> =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState()
                val sent = state.sentKeys.toHashSet()
                messageIds.filterTo(mutableSetOf()) { messageId ->
                    stateKey(profileId, messageId, status.wireValue) in sent ||
                        status == ChatMessageStateAckStatus.Delivered && stateKey(profileId, messageId, ChatMessageStateAckStatus.Read.wireValue) in sent ||
                        state.pending.any { pending ->
                            pending.profileId == profileId && pending.messageId == messageId &&
                                (pending.status == status.wireValue || status == ChatMessageStateAckStatus.Delivered && pending.status == ChatMessageStateAckStatus.Read.wireValue)
                        }
                }
            }
        }

    suspend fun enqueue(ack: ChatMessageStateAck) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState()
                val key = stateKey(ack.profileId, ack.messageId, ack.status)
                if (key in state.sentKeys) return@withContext
                val withoutSuperseded = if (ack.status == ChatMessageStateAckStatus.Read.wireValue) {
                    state.pending.filterNot {
                        it.profileId == ack.profileId &&
                            it.messageId == ack.messageId &&
                            it.status == ChatMessageStateAckStatus.Delivered.wireValue
                    }
                } else {
                    state.pending
                }
                if (withoutSuperseded.any { it.profileId == ack.profileId && it.messageId == ack.messageId && it.status == ack.status }) {
                    writeState(state.copy(pending = withoutSuperseded))
                    return@withContext
                }
                writeState(state.copy(pending = withoutSuperseded + ack.copy(createdAtMillis = System.currentTimeMillis())))
            }
        }
    }

    suspend fun pendingAcks(profileId: String): List<ChatMessageStateAck> = mutex.withLock {
        withContext(Dispatchers.IO) { readState().pending.filter { it.profileId == profileId } }
    }

    suspend fun markSent(ack: ChatMessageStateAck) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState()
                val keysToAdd = buildSet {
                    add(stateKey(ack.profileId, ack.messageId, ack.status))
                    if (ack.status == ChatMessageStateAckStatus.Read.wireValue) {
                        add(stateKey(ack.profileId, ack.messageId, ChatMessageStateAckStatus.Delivered.wireValue))
                    }
                }
                val updatedPending = state.pending.filterNot { pending ->
                    pending.profileId == ack.profileId &&
                        pending.messageId == ack.messageId &&
                        (pending.status == ack.status ||
                            ack.status == ChatMessageStateAckStatus.Read.wireValue &&
                            pending.status == ChatMessageStateAckStatus.Delivered.wireValue)
                }
                writeState(
                    state.copy(
                        pending = updatedPending,
                        sentKeys = (state.sentKeys + keysToAdd).takeLast(MAX_SENT_KEYS)
                    )
                )
            }
        }
    }

    private fun readState(): StoredAckState {
        val file = stateFile()
        if (!file.exists()) return StoredAckState()
        return runCatching { json.decodeFromString<StoredAckState>(file.readText()) }
            .getOrDefault(StoredAckState())
    }

    private fun writeState(state: StoredAckState) {
        val file = stateFile()
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    private fun stateFile(): File =
        File(File(appContext.filesDir, DIRECTORY_NAME), FILE_NAME)

    private fun stateKey(profileId: String, messageId: Long, status: String): String =
        "$profileId:$messageId:$status"

    private companion object {
        const val DIRECTORY_NAME = "supabase_chat_message_state_acks_v1"
        const val FILE_NAME = "acks.json"
        const val MAX_SENT_KEYS = 8_000
    }
}

@Serializable
data class ChatMessageStateAck(
    val profileId: String,
    val messageId: Long,
    val status: String,
    val source: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
private data class StoredAckState(
    val pending: List<ChatMessageStateAck> = emptyList(),
    val sentKeys: List<String> = emptyList()
)
