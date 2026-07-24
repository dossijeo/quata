package com.quata.feature.chat.data

import com.quata.core.model.Conversation
import com.quata.core.model.Message
import com.quata.core.model.MessageDeliveryState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Provider-neutral shape used by the current chat inbox/thread RPC responses.
 *
 * The transport keeps ownership of HTTP/Supabase calls and platform persistence. This shared
 * parser only normalizes the response variants (`root` and `root.update`) so every platform
 * maps the same records before applying its own cache/reconciliation policy.
 */
data class ChatRpcPayloadEnvelope(
    val threads: List<JsonObject>,
    val messages: List<JsonObject>,
    val profiles: List<JsonObject>,
)

/** Transport-neutral profile record embedded in current inbox/thread payloads. */
data class ChatRpcProfile(
    val id: String,
    val displayName: String?,
    val name: String?,
    val avatarUrl: String?,
    val neighborhood: String?,
    val phoneLocal: String?,
    val countryCode: String?,
) {
    fun resolvedDisplayName(): String = displayName?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: phoneLocal?.takeIf { it.isNotBlank() }
        ?: "Usuario"
}

private const val SupabaseChatConversationPrefix = "sb:"

private fun supabaseChatConversationId(threadId: Long): String =
    "$SupabaseChatConversationPrefix$threadId"

fun parseChatRpcPayloadEnvelope(payload: JsonElement): ChatRpcPayloadEnvelope {
    val root = payload as? JsonObject ?: return ChatRpcPayloadEnvelope(emptyList(), emptyList(), emptyList())
    val roots = listOf(root) + listOfNotNull(root.objectAt("update"))
    return ChatRpcPayloadEnvelope(
        threads = roots
            .flatMap { source ->
                source.arrayAt("threads").mapNotNull { it as? JsonObject } +
                    listOfNotNull(source.objectAt("thread"))
            }
            .distinctBy { it.longAt("thread_id") ?: it.longAt("id") },
        messages = roots
            .flatMap { source ->
                source.arrayAt("messages").mapNotNull { it as? JsonObject } +
                    listOfNotNull(source.objectAt("message"))
            }
            .distinctBy { it.longAt("id") },
        profiles = roots
            .flatMap { it.arrayAt("profiles").mapNotNull { profile -> profile as? JsonObject } }
            .distinctBy { profile -> profile.stringAt("id") ?: profile.toString() },
    )
}

/** Collects top-level profiles and sender records embedded in messages for inbox/thread mapping. */
fun ChatRpcPayloadEnvelope.profileRecords(): List<ChatRpcProfile> =
    (profiles + messages.mapNotNull { it.objectAt("sender") })
        .mapNotNull { it.toChatRpcProfile() }
        .distinctBy { it.id }

fun JsonObject.toChatRpcProfile(): ChatRpcProfile? {
    val id = stringAt("id") ?: return null
    return ChatRpcProfile(
        id = id,
        displayName = stringAt("display_name"),
        name = stringAt("name"),
        avatarUrl = stringAt("avatar_url"),
        neighborhood = stringAt("neighborhood"),
        phoneLocal = stringAt("phone_local"),
        countryCode = stringAt("country_code"),
    )
}

/** Maps the portable, persisted fields from an inbox/thread response into UI domain models. */
fun ChatRpcPayloadEnvelope.toChatRpcConversations(currentProfileId: String): List<Conversation> {
    val profilesById = profileRecords().associateBy { it.id }
    return threads.map { it.toChatRpcConversation(currentProfileId, profilesById) }
}

/** Maps the portable, persisted fields from an inbox/thread response into UI domain models. */
fun ChatRpcPayloadEnvelope.toChatRpcMessages(currentProfileId: String): List<Message> {
    val profilesById = profileRecords().associateBy { it.id }
    return messages.map { it.toChatRpcMessage(currentProfileId, profilesById) }
}

private fun JsonObject.toChatRpcConversation(
    currentProfileId: String,
    profiles: Map<String, ChatRpcProfile>,
): Conversation {
    val threadId = longAt("thread_id") ?: longAt("id") ?: 0L
    val type = stringAt("type").orEmpty()
    val participantIds = arrayAt("participants").mapNotNull { it.stringOrNull() }.distinct()
    val otherProfiles = participantIds.filterNot { it == currentProfileId }.mapNotNull(profiles::get)
    val participantTitle = otherProfiles.map(ChatRpcProfile::resolvedDisplayName).distinct().joinToString(", ")
    val backendTitle = stringAt("title")?.takeIf { it.isNotBlank() }
    val explicitGroupTitle = stringAt("subject")?.takeIf { it.isNotBlank() }
        ?: backendTitle?.takeUnless { it == "Chat $threadId" }
    val title = when (type) {
        "private" -> otherProfiles.firstOrNull()?.resolvedDisplayName().orEmpty().ifBlank { backendTitle ?: "Chat" }
        "group" -> explicitGroupTitle ?: participantTitle.ifBlank { "Chat" }
        else -> backendTitle ?: stringAt("subject") ?: "Chat"
    }
    return Conversation(
        id = supabaseChatConversationId(threadId),
        title = title,
        avatarUrl = if (type == "private") otherProfiles.firstOrNull()?.avatarUrl else stringAt("image"),
        lastMessagePreview = stringAt("last_message_preview").orEmpty(),
        unreadCount = intAt("unread") ?: 0,
        updatedAt = stringAt("last_message_at") ?: stringAt("updated_at").orEmpty(),
        updatedAtMillis = longAt("last_time_millis") ?: longAt("updated_at_millis"),
        participantIds = participantIds,
        participantNames = otherProfiles.map(ChatRpcProfile::resolvedDisplayName),
        participantAvatarUrls = otherProfiles.map(ChatRpcProfile::avatarUrl),
        isGroup = type != "private" || participantIds.size > 2,
        isEmergency = type == "sos",
        communityName = title.takeIf { type == "wall" },
        isMuted = booleanAt("is_muted") == true,
        isVisible = booleanAt("is_hidden") != true && booleanAt("is_deleted") != true,
        moderatorIds = arrayAt("moderators").mapNotNull { it.stringOrNull() },
        canMembersInvite = objectAt("meta")?.booleanAt("allowInvite") == true,
    )
}

private fun JsonObject.toChatRpcMessage(
    currentProfileId: String,
    profiles: Map<String, ChatRpcProfile>,
): Message {
    val messageId = longAt("id") ?: 0L
    val threadId = longAt("thread_id") ?: 0L
    val senderId = stringAt("sender_profile_id").orEmpty()
    val sender = objectAt("sender")?.toChatRpcProfile() ?: profiles[senderId]
    val attachment = arrayAt("attachments").firstOrNull() as? JsonObject
    val deliveryState = when (stringAt("delivery_state")?.uppercase()) {
        "READ" -> MessageDeliveryState.Read
        "DELIVERED" -> MessageDeliveryState.Delivered
        else -> MessageDeliveryState.Sent
    }
    return Message(
        id = messageId.toString(),
        conversationId = supabaseChatConversationId(threadId),
        senderId = senderId,
        senderName = sender?.resolvedDisplayName() ?: "Usuario",
        text = stringAt("body").orEmpty(),
        sentAt = stringAt("created_at").orEmpty(),
        sentAtMillis = longAt("created_at_millis"),
        isMine = senderId == currentProfileId,
        isRead = true,
        isEdited = booleanAt("is_edited") == true,
        isDeleted = booleanAt("is_deleted") == true,
        isFavorite = booleanAt("favorited") == true,
        replyToMessageId = longAt("reply_to_message_id")?.toString(),
        replyToSenderName = stringAt("reply_to_sender_name")
            ?: objectAt("reply_to_sender")?.let { it.stringAt("display_name") ?: it.stringAt("name") ?: it.stringAt("phone_local") },
        replyToText = stringAt("reply_to_body"),
        forwardedFromSenderId = stringAt("forwarded_from_profile_id"),
        attachmentUri = attachment?.stringAt("url"),
        attachmentName = attachment?.stringAt("name"),
        attachmentMimeType = attachment?.stringAt("mime_type"),
        clientMessageId = stringAt("client_message_id"),
        deliveryState = if (senderId == currentProfileId) deliveryState else MessageDeliveryState.Sent,
    )
}

private fun JsonObject.objectAt(key: String): JsonObject? = get(key) as? JsonObject

private fun JsonObject.arrayAt(key: String): List<JsonElement> =
    (get(key) as? JsonArray)?.toList().orEmpty()

private fun JsonObject.longAt(key: String): Long? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull

private fun JsonObject.intAt(key: String): Int? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull

private fun JsonObject.booleanAt(key: String): Boolean? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull

private fun JsonObject.stringAt(key: String): String? =
    (get(key) as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

private fun JsonElement.stringOrNull(): String? =
    (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
