package com.quata.feature.chat.data

import android.content.Context
import com.quata.bettermessages.BetterMessagesJson
import com.quata.bettermessages.BmThreadResponse
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.data.supabase.CommunityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

internal class BetterMessagesConversationCacheStore(
    private val appContext: Context,
    private val json: Json = BetterMessagesJson.default
) {
    private val mutex = Mutex()

    suspend fun cachedConversations(profileId: String): List<Conversation> = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState(profileId).conversations.map { it.toDomain() }
        }
    }

    suspend fun isConversationCacheExpired(profileId: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val state = readState(profileId)
            state.conversations.isEmpty() || isExpired(state.conversationsCachedAt)
        }
    }

    suspend fun replaceConversations(profileId: String, conversations: List<Conversation>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val state = readState(profileId)
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        conversationsCachedAt = now,
                        conversations = conversations.map { it.toStored() }
                    )
                )
            }
        }
    }

    suspend fun upsertConversation(profileId: String, conversation: Conversation) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val state = readState(profileId)
                val updated = (listOf(conversation) + state.conversations.map { it.toDomain() }.filterNot { it.id == conversation.id })
                    .distinctBy { it.id }
                    .sortedByDescending { it.updatedAtMillis ?: 0L }
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        conversationsCachedAt = now,
                        conversations = updated.map { it.toStored() }
                    )
                )
            }
        }
    }

    suspend fun removeConversation(profileId: String, conversationId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val state = readState(profileId)
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        conversationsCachedAt = now,
                        conversations = state.conversations.filterNot { it.id == conversationId }
                    )
                )
            }
        }
    }

    suspend fun cachedThreadResponse(profileId: String, threadId: Int): BmThreadResponse? = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState(profileId).threads[threadId.toString()]?.response
        }
    }

    suspend fun upsertThreadResponse(profileId: String, threadId: Int, response: BmThreadResponse) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        threads = state.threads + (threadId.toString() to StoredThreadResponse(
                            cachedAt = System.currentTimeMillis(),
                            response = response
                        ))
                    )
                )
            }
        }
    }

    suspend fun cachedFavoriteMessages(profileId: String): List<Message>? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val state = readState(profileId)
            if (state.favoriteMessagesCachedAt <= 0L) {
                null
            } else {
                state.favoriteMessages.map { it.toDomain() }.sortedFavoriteMessages()
            }
        }
    }

    suspend fun hasFavoriteMessagesCache(profileId: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState(profileId).favoriteMessagesCachedAt > 0L
        }
    }

    suspend fun replaceFavoriteMessages(profileId: String, messages: List<Message>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        favoriteMessagesCachedAt = System.currentTimeMillis(),
                        favoriteMessages = messages
                            .filter { it.isFavorite && !it.isDeleted }
                            .distinctBy { it.id }
                            .sortedFavoriteMessages()
                            .map { it.toStored() }
                    )
                )
            }
        }
    }

    suspend fun upsertFavoriteMessage(profileId: String, message: Message) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                if (state.favoriteMessagesCachedAt <= 0L) return@withContext
                val updated = (listOf(message) + state.favoriteMessages.map { it.toDomain() }.filterNot { it.id == message.id })
                    .filter { it.isFavorite && !it.isDeleted }
                    .distinctBy { it.id }
                    .sortedFavoriteMessages()
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        favoriteMessagesCachedAt = System.currentTimeMillis(),
                        favoriteMessages = updated.map { it.toStored() }
                    )
                )
            }
        }
    }

    suspend fun removeFavoriteMessage(profileId: String, messageId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                if (state.favoriteMessagesCachedAt <= 0L) return@withContext
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        favoriteMessagesCachedAt = System.currentTimeMillis(),
                        favoriteMessages = state.favoriteMessages.filterNot { it.id == messageId }
                    )
                )
            }
        }
    }

    suspend fun removeFavoriteMessagesForConversation(profileId: String, conversationId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                if (state.favoriteMessagesCachedAt <= 0L) return@withContext
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        favoriteMessagesCachedAt = System.currentTimeMillis(),
                        favoriteMessages = state.favoriteMessages.filterNot { it.conversationId == conversationId }
                    )
                )
            }
        }
    }

    suspend fun cachedProfileDirectory(profileId: String): List<CommunityProfile> = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState(profileId).profiles
        }
    }

    suspend fun isProfileDirectoryExpired(profileId: String): Boolean = mutex.withLock {
        withContext(Dispatchers.IO) {
            val state = readState(profileId)
            state.profiles.isEmpty() || isExpired(state.profilesCachedAt)
        }
    }

    suspend fun replaceProfileDirectory(profileId: String, profiles: List<CommunityProfile>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                writeState(
                    profileId = profileId,
                    state = state.copy(
                        profilesCachedAt = System.currentTimeMillis(),
                        profiles = profiles.distinctBy { it.id }
                    )
                )
            }
        }
    }

    private fun isExpired(cachedAt: Long): Boolean =
        cachedAt <= 0L || System.currentTimeMillis() - cachedAt > RETENTION_MILLIS

    private fun readState(profileId: String): StoredConversationCache {
        val file = stateFile(profileId)
        if (!file.exists()) return StoredConversationCache()
        return runCatching { json.decodeFromString<StoredConversationCache>(file.readText()) }
            .getOrDefault(StoredConversationCache())
    }

    private fun writeState(profileId: String, state: StoredConversationCache) {
        val file = stateFile(profileId)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(state))
    }

    private fun stateFile(profileId: String): File {
        val safeProfileId = profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(appContext.filesDir, DIRECTORY_NAME), "$safeProfileId.json")
    }

    private fun Conversation.toStored(): StoredConversation = StoredConversation(
        id = id,
        title = title,
        avatarUrl = avatarUrl,
        lastMessagePreview = lastMessagePreview,
        unreadCount = unreadCount,
        updatedAt = updatedAt,
        updatedAtMillis = updatedAtMillis,
        participantIds = participantIds,
        participantNames = participantNames,
        participantAvatarUrls = participantAvatarUrls,
        isGroup = isGroup,
        isEmergency = isEmergency,
        communityName = communityName,
        isMuted = isMuted,
        isVisible = isVisible,
        moderatorIds = moderatorIds,
        canMembersInvite = canMembersInvite,
        blockedUserIds = blockedUserIds
    )

    private fun StoredConversation.toDomain(): Conversation = Conversation(
        id = id,
        title = title,
        avatarUrl = avatarUrl,
        lastMessagePreview = lastMessagePreview,
        unreadCount = unreadCount,
        updatedAt = updatedAt,
        updatedAtMillis = updatedAtMillis,
        participantIds = participantIds,
        participantNames = participantNames,
        participantAvatarUrls = participantAvatarUrls,
        isGroup = isGroup,
        isEmergency = isEmergency,
        communityName = communityName,
        isMuted = isMuted,
        isVisible = isVisible,
        moderatorIds = moderatorIds,
        canMembersInvite = canMembersInvite,
        blockedUserIds = blockedUserIds
    )

    private fun Message.toStored(): StoredMessage = StoredMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        text = text,
        sentAt = sentAt,
        sentAtMillis = sentAtMillis,
        isMine = isMine,
        isRead = isRead,
        isEdited = isEdited,
        isDeleted = isDeleted,
        isFavorite = isFavorite,
        replyToMessageId = replyToMessageId,
        replyToSenderName = replyToSenderName,
        replyToText = replyToText,
        forwardedFromSenderId = forwardedFromSenderId,
        forwardedFromSenderName = forwardedFromSenderName,
        attachmentUri = attachmentUri,
        attachmentName = attachmentName,
        attachmentMimeType = attachmentMimeType,
        isPending = isPending,
        isLocalEcho = isLocalEcho
    )

    private fun StoredMessage.toDomain(): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        senderName = senderName,
        text = text,
        sentAt = sentAt,
        sentAtMillis = sentAtMillis,
        isMine = isMine,
        isRead = isRead,
        isEdited = isEdited,
        isDeleted = isDeleted,
        isFavorite = isFavorite,
        replyToMessageId = replyToMessageId,
        replyToSenderName = replyToSenderName,
        replyToText = replyToText,
        forwardedFromSenderId = forwardedFromSenderId,
        forwardedFromSenderName = forwardedFromSenderName,
        attachmentUri = attachmentUri,
        attachmentName = attachmentName,
        attachmentMimeType = attachmentMimeType,
        isPending = isPending,
        isLocalEcho = isLocalEcho
    )

    private fun List<Message>.sortedFavoriteMessages(): List<Message> =
        sortedWith(
            compareByDescending<Message> { it.sentAtMillis ?: 0L }
                .thenByDescending { it.id }
        )

    @Serializable
    private data class StoredConversationCache(
        val conversationsCachedAt: Long = 0L,
        val conversations: List<StoredConversation> = emptyList(),
        val profilesCachedAt: Long = 0L,
        val profiles: List<CommunityProfile> = emptyList(),
        val threads: Map<String, StoredThreadResponse> = emptyMap(),
        val favoriteMessagesCachedAt: Long = 0L,
        val favoriteMessages: List<StoredMessage> = emptyList()
    )

    @Serializable
    private data class StoredThreadResponse(
        val cachedAt: Long,
        val response: BmThreadResponse
    )

    @Serializable
    private data class StoredConversation(
        val id: String,
        val title: String,
        val avatarUrl: String? = null,
        val lastMessagePreview: String,
        val unreadCount: Int = 0,
        val updatedAt: String = "",
        val updatedAtMillis: Long? = null,
        val participantIds: List<String> = emptyList(),
        val participantNames: List<String> = emptyList(),
        val participantAvatarUrls: List<String?> = emptyList(),
        val isGroup: Boolean = false,
        val isEmergency: Boolean = false,
        val communityName: String? = null,
        val isMuted: Boolean = false,
        val isVisible: Boolean = true,
        val moderatorIds: List<String> = emptyList(),
        val canMembersInvite: Boolean = false,
        val blockedUserIds: List<String> = emptyList()
    )

    @Serializable
    private data class StoredMessage(
        val id: String,
        val conversationId: String,
        val senderId: String,
        val senderName: String,
        val text: String,
        val sentAt: String,
        val sentAtMillis: Long? = null,
        val isMine: Boolean = false,
        val isRead: Boolean = true,
        val isEdited: Boolean = false,
        val isDeleted: Boolean = false,
        val isFavorite: Boolean = false,
        val replyToMessageId: String? = null,
        val replyToSenderName: String? = null,
        val replyToText: String? = null,
        val forwardedFromSenderId: String? = null,
        val forwardedFromSenderName: String? = null,
        val attachmentUri: String? = null,
        val attachmentName: String? = null,
        val attachmentMimeType: String? = null,
        val isPending: Boolean = false,
        val isLocalEcho: Boolean = false
    )

    private companion object {
        const val DIRECTORY_NAME = "better_messages_conversation_cache"
        const val RETENTION_MILLIS = 24L * 60L * 60L * 1000L
    }
}
