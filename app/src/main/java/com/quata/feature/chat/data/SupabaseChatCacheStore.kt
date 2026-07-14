package com.quata.feature.chat.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.MessageDeliveryState
import com.quata.data.supabase.CommunityProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class SupabaseChatCacheStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }
) {
    private val appContext = context.applicationContext
    private val helper = ChatCacheOpenHelper(appContext)

    suspend fun cachedConversations(profileId: String): List<Conversation> = io {
        migrateLegacyCache(profileId)
        readPayload(profileId, KEY_CONVERSATIONS)
            ?.let { runCatching { json.decodeFromString<List<StoredConversation>>(it) }.getOrNull() }
            .orEmpty().map { it.toDomain() }
    }

    suspend fun replaceConversations(profileId: String, conversations: List<Conversation>) =
        writePayload(profileId, KEY_CONVERSATIONS, json.encodeToString(conversations.map { it.toStored() }))

    suspend fun upsertConversation(profileId: String, conversation: Conversation) {
        val updated = (listOf(conversation) + cachedConversations(profileId).filterNot { it.id == conversation.id })
            .distinctBy { it.id }.sortedByDescending { it.updatedAtMillis ?: 0L }
        replaceConversations(profileId, updated)
    }

    suspend fun removeConversation(profileId: String, conversationId: String) {
        replaceConversations(profileId, cachedConversations(profileId).filterNot { it.id == conversationId })
        io { helper.writableDatabase.delete(TABLE_CACHE, "profile_id = ? AND cache_key = ?", arrayOf(profileId, messagesKey(conversationId))) }
    }

    suspend fun cachedMessages(profileId: String, conversationId: String): List<Message> = io {
        migrateLegacyCache(profileId)
        readPayload(profileId, messagesKey(conversationId))
            ?.let { runCatching { json.decodeFromString<List<StoredMessage>>(it) }.getOrNull() }
            .orEmpty().map { it.toDomain() }
    }

    suspend fun replaceMessages(profileId: String, conversationId: String, messages: List<Message>) {
        val pending = cachedMessages(profileId, conversationId).filter { it.isPending || it.isLocalEcho }
        val merged = (messages + pending)
            .distinctBy { it.clientMessageId?.let { id -> "client:$id" } ?: "message:${it.id}" }
            .sortedBy { it.sentAtMillis ?: Long.MAX_VALUE }
        writePayload(profileId, messagesKey(conversationId), json.encodeToString(merged.map { it.toStored() }))
    }

    suspend fun upsertMessage(profileId: String, message: Message) {
        val current = cachedMessages(profileId, message.conversationId)
        replaceMessages(profileId, message.conversationId, current.filterNot { it.id == message.id } + message)
    }

    suspend fun removePendingMessage(profileId: String, conversationId: String, clientMessageId: String) {
        val updated = cachedMessages(profileId, conversationId).filterNot { it.clientMessageId == clientMessageId && it.isLocalEcho }
        writePayload(profileId, messagesKey(conversationId), json.encodeToString(updated.map { it.toStored() }))
    }

    suspend fun cachedFavoriteMessages(profileId: String): List<Message> = io {
        migrateLegacyCache(profileId)
        readPayload(profileId, KEY_FAVORITES)
            ?.let { runCatching { json.decodeFromString<List<StoredMessage>>(it) }.getOrNull() }
            .orEmpty().map { it.toDomain() }
    }

    suspend fun replaceFavoriteMessages(profileId: String, messages: List<Message>) =
        writePayload(profileId, KEY_FAVORITES, json.encodeToString(messages.map { it.toStored() }))

    suspend fun cachedProfileDirectory(profileId: String): List<CommunityProfile> = io {
        migrateLegacyCache(profileId)
        readPayload(profileId, KEY_PROFILES)
            ?.let { runCatching { json.decodeFromString<List<CommunityProfile>>(it) }.getOrNull() }
            .orEmpty()
    }

    suspend fun replaceProfileDirectory(profileId: String, profiles: List<CommunityProfile>) =
        writePayload(profileId, KEY_PROFILES, json.encodeToString(profiles.distinctBy { it.id }))

    suspend fun enqueueOutgoing(message: PendingOutgoingMessage) = io {
        helper.writableDatabase.insertWithOnConflict(TABLE_OUTBOX, null, message.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun pendingOutgoing(profileId: String): List<PendingOutgoingMessage> = io {
        val result = mutableListOf<PendingOutgoingMessage>()
        helper.readableDatabase.query(TABLE_OUTBOX, OUTBOX_COLUMNS, "profile_id = ?", arrayOf(profileId), null, null, "created_at_millis ASC")
            .use { cursor -> while (cursor.moveToNext()) result += cursor.toPendingOutgoing() }
        result
    }

    suspend fun markOutgoingAttempt(profileId: String, clientMessageId: String, error: String?) = io {
        helper.writableDatabase.execSQL(
            "UPDATE $TABLE_OUTBOX SET attempts = attempts + 1, last_error = ? WHERE profile_id = ? AND client_message_id = ?",
            arrayOf<Any?>(error, profileId, clientMessageId)
        )
    }

    suspend fun markOutgoingUploaded(profileId: String, clientMessageId: String, remoteFileId: Long) = io {
        helper.writableDatabase.execSQL(
            "UPDATE $TABLE_OUTBOX SET remote_file_id = ? WHERE profile_id = ? AND client_message_id = ?",
            arrayOf<Any?>(remoteFileId, profileId, clientMessageId)
        )
    }

    suspend fun resetOutgoingAttempts(profileId: String, clientMessageId: String) = io {
        helper.writableDatabase.execSQL(
            "UPDATE $TABLE_OUTBOX SET attempts = 0, last_error = NULL WHERE profile_id = ? AND client_message_id = ?",
            arrayOf(profileId, clientMessageId)
        )
    }

    suspend fun removeOutgoing(profileId: String, clientMessageId: String) = io {
        helper.writableDatabase.delete(TABLE_OUTBOX, "profile_id = ? AND client_message_id = ?", arrayOf(profileId, clientMessageId))
    }

    private suspend fun writePayload(profileId: String, key: String, payload: String) = io {
        helper.writableDatabase.insertWithOnConflict(TABLE_CACHE, null, ContentValues().apply {
            put("profile_id", profileId); put("cache_key", key); put("payload_json", payload); put("updated_at_millis", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun readPayload(profileId: String, key: String): String? =
        helper.readableDatabase.query(TABLE_CACHE, arrayOf("payload_json"), "profile_id = ? AND cache_key = ?", arrayOf(profileId, key), null, null, null, "1")
            .use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    @Synchronized
    private fun migrateLegacyCache(profileId: String) {
        if (readPayload(profileId, KEY_MIGRATED) != null) return
        val safeProfileId = profileId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val legacyFile = java.io.File(java.io.File(appContext.filesDir, "supabase_chat_cache_v1"), "$safeProfileId.json")
        val legacy = legacyFile.takeIf(java.io.File::isFile)?.let { file ->
            runCatching { json.decodeFromString<LegacyStoredChatCache>(file.readText()) }.getOrNull()
        }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            fun writeCacheEntry(key: String, payload: String) {
                db.insertWithOnConflict(TABLE_CACHE, null, ContentValues().apply {
                    this.put("profile_id", profileId)
                    this.put("cache_key", key)
                    this.put("payload_json", payload)
                    this.put("updated_at_millis", System.currentTimeMillis())
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            if (legacy != null) {
                writeCacheEntry(KEY_CONVERSATIONS, json.encodeToString(legacy.conversations))
                writeCacheEntry(KEY_FAVORITES, json.encodeToString(legacy.favoriteMessages))
                writeCacheEntry(KEY_PROFILES, json.encodeToString(legacy.profiles))
                legacy.messagesByConversation.forEach { (conversationId, messages) -> writeCacheEntry(messagesKey(conversationId), json.encodeToString(messages)) }
            }
            writeCacheEntry(KEY_MIGRATED, "true")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (legacy != null) runCatching { legacyFile.delete() }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
    private fun messagesKey(conversationId: String) = "messages:$conversationId"

    private fun PendingOutgoingMessage.toValues() = ContentValues().apply {
        put("profile_id", profileId); put("client_message_id", clientMessageId); put("conversation_id", conversationId); put("text", text)
        put("attachment_uri", attachmentUri); put("attachment_name", attachmentName); put("attachment_mime_type", attachmentMimeType)
        put("reply_to_message_id", replyToMessageId); put("reply_to_sender_name", replyToSenderName); put("reply_to_text", replyToText)
        put("remote_file_id", remoteFileId); put("created_at_millis", createdAtMillis); put("attempts", attempts); put("last_error", lastError)
    }

    private fun Cursor.toPendingOutgoing() = PendingOutgoingMessage(
        profileId = getString(getColumnIndexOrThrow("profile_id")), clientMessageId = getString(getColumnIndexOrThrow("client_message_id")),
        conversationId = getString(getColumnIndexOrThrow("conversation_id")), text = getString(getColumnIndexOrThrow("text")),
        attachmentUri = getString(getColumnIndexOrThrow("attachment_uri")), attachmentName = getString(getColumnIndexOrThrow("attachment_name")),
        attachmentMimeType = getString(getColumnIndexOrThrow("attachment_mime_type")), replyToMessageId = getString(getColumnIndexOrThrow("reply_to_message_id")),
        replyToSenderName = getString(getColumnIndexOrThrow("reply_to_sender_name")), replyToText = getString(getColumnIndexOrThrow("reply_to_text")),
        remoteFileId = getLong(getColumnIndexOrThrow("remote_file_id")).takeIf { !isNull(getColumnIndexOrThrow("remote_file_id")) },
        createdAtMillis = getLong(getColumnIndexOrThrow("created_at_millis")), attempts = getInt(getColumnIndexOrThrow("attempts")),
        lastError = getString(getColumnIndexOrThrow("last_error"))
    )

    private class ChatCacheOpenHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onConfigure(db: SQLiteDatabase) { db.enableWriteAheadLogging() }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE $TABLE_CACHE (profile_id TEXT NOT NULL, cache_key TEXT NOT NULL, payload_json TEXT NOT NULL, updated_at_millis INTEGER NOT NULL, PRIMARY KEY(profile_id, cache_key))")
            db.execSQL("CREATE INDEX idx_chat_cache_updated ON $TABLE_CACHE(profile_id, updated_at_millis DESC)")
            db.execSQL("CREATE TABLE $TABLE_OUTBOX (profile_id TEXT NOT NULL, client_message_id TEXT NOT NULL, conversation_id TEXT NOT NULL, text TEXT NOT NULL, attachment_uri TEXT, attachment_name TEXT, attachment_mime_type TEXT, reply_to_message_id TEXT, reply_to_sender_name TEXT, reply_to_text TEXT, remote_file_id INTEGER, created_at_millis INTEGER NOT NULL, attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT, PRIMARY KEY(profile_id, client_message_id))")
            db.execSQL("CREATE INDEX idx_chat_outbox_created ON $TABLE_OUTBOX(profile_id, created_at_millis ASC)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "quata_chat_cache.db"; const val DATABASE_VERSION = 1
        const val TABLE_CACHE = "chat_cache"; const val TABLE_OUTBOX = "chat_outbox"
        const val KEY_CONVERSATIONS = "conversations"; const val KEY_FAVORITES = "favorites"; const val KEY_PROFILES = "profiles"
        const val KEY_MIGRATED = "legacy_json_migrated_v1"
        val OUTBOX_COLUMNS = arrayOf("profile_id", "client_message_id", "conversation_id", "text", "attachment_uri", "attachment_name", "attachment_mime_type", "reply_to_message_id", "reply_to_sender_name", "reply_to_text", "remote_file_id", "created_at_millis", "attempts", "last_error")
    }
}

internal data class PendingOutgoingMessage(
    val profileId: String, val clientMessageId: String, val conversationId: String, val text: String,
    val attachmentUri: String? = null, val attachmentName: String? = null, val attachmentMimeType: String? = null,
    val replyToMessageId: String? = null, val replyToSenderName: String? = null, val replyToText: String? = null,
    val remoteFileId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(), val attempts: Int = 0, val lastError: String? = null
)

@Serializable
private data class LegacyStoredChatCache(
    val conversations: List<StoredConversation> = emptyList(),
    val messagesByConversation: Map<String, List<StoredMessage>> = emptyMap(),
    val favoriteMessages: List<StoredMessage> = emptyList(),
    val profiles: List<CommunityProfile> = emptyList()
)

@Serializable
private data class StoredConversation(
    val id: String, val title: String, val avatarUrl: String? = null, val lastMessagePreview: String, val unreadCount: Int = 0,
    val updatedAt: String = "", val updatedAtMillis: Long? = null, val participantIds: List<String> = emptyList(),
    val participantNames: List<String> = emptyList(), val participantAvatarUrls: List<String?> = emptyList(), val isGroup: Boolean = false,
    val isEmergency: Boolean = false, val communityName: String? = null, val isMuted: Boolean = false, val isVisible: Boolean = true,
    val moderatorIds: List<String> = emptyList(), val canMembersInvite: Boolean = false, val blockedUserIds: List<String> = emptyList()
)
private fun Conversation.toStored() = StoredConversation(id, title, avatarUrl, lastMessagePreview, unreadCount, updatedAt, updatedAtMillis, participantIds, participantNames, participantAvatarUrls, isGroup, isEmergency, communityName, isMuted, isVisible, moderatorIds, canMembersInvite, blockedUserIds)
private fun StoredConversation.toDomain() = Conversation(id, title, avatarUrl, lastMessagePreview, unreadCount, updatedAt, updatedAtMillis, participantIds, participantNames, participantAvatarUrls, isGroup, isEmergency, communityName, isMuted, isVisible, moderatorIds, canMembersInvite, blockedUserIds)

@Serializable
private data class StoredMessage(
    val id: String, val conversationId: String, val senderId: String, val senderName: String, val text: String, val sentAt: String,
    val sentAtMillis: Long? = null, val isMine: Boolean = false, val isRead: Boolean = true, val isEdited: Boolean = false,
    val isDeleted: Boolean = false, val isFavorite: Boolean = false, val replyToMessageId: String? = null,
    val replyToSenderName: String? = null, val replyToText: String? = null, val forwardedFromSenderId: String? = null,
    val forwardedFromSenderName: String? = null, val attachmentUri: String? = null, val attachmentName: String? = null,
    val attachmentMimeType: String? = null, val clientMessageId: String? = null, val isPending: Boolean = false,
    val isLocalEcho: Boolean = false, val deliveryState: MessageDeliveryState = if (isPending) MessageDeliveryState.Pending else MessageDeliveryState.Sent
)
private fun Message.toStored() = StoredMessage(id, conversationId, senderId, senderName, text, sentAt, sentAtMillis, isMine, isRead, isEdited, isDeleted, isFavorite, replyToMessageId, replyToSenderName, replyToText, forwardedFromSenderId, forwardedFromSenderName, attachmentUri, attachmentName, attachmentMimeType, clientMessageId, isPending, isLocalEcho, deliveryState)
private fun StoredMessage.toDomain() = Message(id, conversationId, senderId, senderName, text, sentAt, sentAtMillis, isMine, isRead, isEdited, isDeleted, isFavorite, replyToMessageId, replyToSenderName, replyToText, forwardedFromSenderId, forwardedFromSenderName, attachmentUri, attachmentName, attachmentMimeType, clientMessageId, isPending, isLocalEcho, deliveryState)
