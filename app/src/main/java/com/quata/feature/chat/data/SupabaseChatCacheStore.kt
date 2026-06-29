package com.quata.feature.chat.data

import android.content.Context
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

internal class SupabaseChatCacheStore(
    private val appContext: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
) {
    private val mutex = Mutex()

    suspend fun cachedConversations(profileId: String): List<Conversation> = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState(profileId).conversations.map { it.toDomain() }
        }
    }

    suspend fun replaceConversations(profileId: String, conversations: List<Conversation>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                writeState(profileId, state.copy(conversations = conversations.map { it.toStored() }))
            }
        }
    }

    suspend fun upsertConversation(profileId: String, conversation: Conversation) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                val updated = (listOf(conversation) + state.conversations.map { it.toDomain() }.filterNot { it.id == conversation.id })
                    .distinctBy { it.id }
                    .sortedByDescending { it.updatedAtMillis ?: 0L }
                writeState(profileId, state.copy(conversations = updated.map { it.toStored() }))
            }
        }
    }

    suspend fun removeConversation(profileId: String, conversationId: String) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                writeState(profileId, state.copy(conversations = state.conversations.filterNot { it.id == conversationId }))
            }
        }
    }

    suspend fun cachedMessages(profileId: String, conversationId: String): List<Message> = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState(profileId).messagesByConversation[conversationId].orEmpty().map { it.toDomain() }
        }
    }

    suspend fun replaceMessages(profileId: String, conversationId: String, messages: List<Message>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                writeState(
                    profileId,
                    state.copy(messagesByConversation = state.messagesByConversation + (conversationId to messages.map { it.toStored() }))
                )
            }
        }
    }

    suspend fun cachedFavoriteMessages(profileId: String): List<Message> = mutex.withLock {
        withContext(Dispatchers.IO) {
            readState(profileId).favoriteMessages.map { it.toDomain() }
        }
    }

    suspend fun replaceFavoriteMessages(profileId: String, messages: List<Message>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                writeState(profileId, state.copy(favoriteMessages = messages.map { it.toStored() }))
            }
        }
    }

    suspend fun cachedProfileDirectory(profileId: String): List<CommunityProfile> = mutex.withLock {
        withContext(Dispatchers.IO) { readState(profileId).profiles }
    }

    suspend fun replaceProfileDirectory(profileId: String, profiles: List<CommunityProfile>) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val state = readState(profileId)
                writeState(profileId, state.copy(profiles = profiles.distinctBy { it.id }))
            }
        }
    }

    private fun readState(profileId: String): StoredChatCache {
        val file = stateFile(profileId)
        if (!file.exists()) return StoredChatCache()
        return runCatching { json.decodeFromString<StoredChatCache>(file.readText()) }
            .getOrDefault(StoredChatCache())
    }

    private fun writeState(profileId: String, state: StoredChatCache) {
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

    @Serializable
    private data class StoredChatCache(
        val conversations: List<StoredConversation> = emptyList(),
        val messagesByConversation: Map<String, List<StoredMessage>> = emptyMap(),
        val favoriteMessages: List<StoredMessage> = emptyList(),
        val profiles: List<CommunityProfile> = emptyList()
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
        const val DIRECTORY_NAME = "supabase_chat_cache_v1"
    }
}
