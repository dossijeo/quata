package com.quata.feature.chat.data

import com.quata.core.model.Conversation
import com.quata.data.supabase.CommunityWallStats
import java.time.Instant
import java.time.format.DateTimeParseException

const val SupabaseChatConversationPrefix = "sb:"
const val WallConversationPrefix = "wall:"

fun supabaseChatConversationId(threadId: Long): String = "$SupabaseChatConversationPrefix$threadId"
fun wallConversationId(wallId: String): String = "$WallConversationPrefix$wallId"

fun String.supabaseThreadIdOrNull(): Long? =
    takeIf { it.startsWith(SupabaseChatConversationPrefix) }
        ?.removePrefix(SupabaseChatConversationPrefix)
        ?.toLongOrNull()
        ?.takeIf { it > 0L }

fun String.wallIdOrNull(): String? =
    takeIf { it.startsWith(WallConversationPrefix) }
        ?.removePrefix(WallConversationPrefix)
        ?.takeIf { it.isNotBlank() }

fun CommunityWallStats.toConversation(): Conversation =
    Conversation(
        id = wallConversationId(id),
        title = name ?: slug ?: "Comunidad",
        lastMessagePreview = "",
        updatedAt = chat_last_at.orEmpty(),
        updatedAtMillis = chat_last_at?.toEpochMillisOrNull(),
        isGroup = true,
        communityName = name,
        isVisible = true,
        canMembersInvite = false
    )

fun String.toEpochMillisOrNull(): Long? =
    try {
        Instant.parse(this).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
