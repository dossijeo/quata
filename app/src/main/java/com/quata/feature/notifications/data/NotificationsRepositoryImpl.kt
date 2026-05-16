package com.quata.feature.notifications.data

import android.content.Context
import com.quata.R
import com.quata.core.common.mapFailureToUserFacing
import com.quata.core.config.AppConfig
import com.quata.core.model.Conversation
import com.quata.core.model.NotificationItem
import com.quata.core.session.SessionManager
import com.quata.data.supabase.CommunityNotification
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.feature.chat.data.wallConversationId
import com.quata.feature.chat.domain.ChatRepository
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class NotificationsRepositoryImpl(
    private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val supabaseApi: SupabaseCommunityApi,
    private val sessionManager: SessionManager
) : NotificationsRepository {
    override suspend fun getNotifications(): Result<List<NotificationItem>> =
        if (AppConfig.USE_MOCK_BACKEND) {
            chatRepository.getConversations().map { conversations ->
                conversations.toNotificationItems(chatRepository.activeConversationId.value)
            }
        } else {
            runCatching { loadSupabaseNotifications() }
                .mapFailureToUserFacing(appContext, R.string.error_load_notifications)
        }

    override suspend fun getNotificationCount(): Result<Int> =
        getNotifications().map { notifications -> notifications.sumOf { it.unreadCount } }

    override fun observeNotifications(): Flow<List<NotificationItem>> =
        if (AppConfig.USE_MOCK_BACKEND) {
            combine(
                chatRepository.observeConversations(),
                chatRepository.activeConversationId
            ) { conversations, activeConversationId ->
                conversations.toNotificationItems(activeConversationId)
            }
        } else {
            flow {
                while (true) {
                    runCatching { loadSupabaseNotifications() }
                        .onSuccess { emit(it) }
                        .onFailure { emit(emptyList()) }
                    delay(8_000)
                }
            }
        }

    override fun observeNotificationCount(): Flow<Int> =
        observeNotifications()
            .map { notifications -> notifications.sumOf { it.unreadCount } }
            .catch { emit(0) }

    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            chatRepository.markConversationRead(notification.conversationId).getOrThrow()
        } else {
            val session = sessionManager.currentSession() ?: error("No hay sesion activa")
            val (type, wallId) = notification.id.parseSupabaseNotificationKey()
            supabaseApi.markNotificationsRead(session.userId, type = type, wallId = wallId)
        }
        Unit
    }.mapFailureToUserFacing(appContext, R.string.error_load_notifications)

    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> =
        markNotificationRead(notification)

    private suspend fun loadSupabaseNotifications(): List<NotificationItem> {
        val session = sessionManager.currentSession() ?: return emptyList()
        return supabaseApi.getNotifications(session.userId)
            .map { it.toNotificationItem() }
    }
}

private fun CommunityNotification.toNotificationItem(): NotificationItem {
    val typeLabel = type?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "QUATA"
    val conversationId = wall_id?.let { wallConversationId(it) } ?: ""
    return NotificationItem(
        id = supabaseNotificationKey(id, type, wall_id),
        conversationId = conversationId,
        title = typeLabel,
        body = message ?: emoji ?: typeLabel,
        createdAt = created_at.orEmpty(),
        isRead = is_read == true,
        unreadCount = if (is_read == true) 0 else 1
    )
}

private fun List<Conversation>.toNotificationItems(activeConversationId: String?): List<NotificationItem> =
    filter { conversation ->
        conversation.id != activeConversationId &&
            conversation.isVisible &&
            !conversation.isMuted &&
            conversation.unreadCount > 0
    }.map { conversation ->
        NotificationItem(
            id = "notification_${conversation.id}",
            conversationId = conversation.id,
            title = conversation.notificationTitle(),
            body = conversation.lastMessagePreview.ifBlank { conversation.title },
            createdAt = conversation.updatedAt.ifBlank { "Ahora" },
            unreadCount = conversation.unreadCount
        )
    }

private fun Conversation.notificationTitle(): String = when {
    isEmergency -> title.ifBlank { "SOS" }
    communityName?.isNotBlank() == true -> communityName
    isGroup -> participantNames.take(3).joinToString(", ").ifBlank { title }
    else -> title
}.orEmpty()

private fun supabaseNotificationKey(id: String, type: String?, wallId: String?): String =
    listOf(id, type.orEmpty(), wallId.orEmpty()).joinToString("|")

private fun String.parseSupabaseNotificationKey(): Pair<String?, String?> {
    val parts = split("|")
    return parts.getOrNull(1)?.takeIf { it.isNotBlank() } to parts.getOrNull(2)?.takeIf { it.isNotBlank() }
}
