package com.quata.feature.notifications.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.NotificationItem
import com.quata.core.text.SosPreviewCatalog
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import platform.UIKit.UIViewController

fun QuataIosNotificationsEvidenceViewController(
    onOpenConversation: (String) -> Unit,
): UIViewController {
    val repository = IosNotificationsEvidenceRepository()
    return ComposeUIViewController {
        QuataTheme {
            NotificationsHostContent(
                padding = PaddingValues(),
                repository = repository,
                timestampNowMillis = 1_780_000_000_000L,
                strings = iosNotificationsEvidenceStrings(),
                onBack = {},
                onOpenConversation = onOpenConversation,
            )
        }
    }
}

private class IosNotificationsEvidenceRepository : NotificationsRepository {
    private val items = MutableStateFlow(listOf(
        NotificationItem(
            id = "notification_conversation-ios",
            conversationId = "conversation-ios",
            title = "Nsue",
            body = "Mensaje pendiente de lectura",
            createdAt = "2026-08-10T07:15:00Z",
            unreadCount = 2,
        ),
    ))

    override suspend fun getNotifications(): Result<List<NotificationItem>> = Result.success(items.value)
    override suspend fun getNotificationCount(): Result<Int> = Result.success(items.value.sumOf(NotificationItem::unreadCount))
    override fun observeNotifications(): Flow<List<NotificationItem>> = items
    override fun observeNotificationCount(): Flow<Int> = flow { emit(items.value.sumOf(NotificationItem::unreadCount)) }
    override suspend fun markNotificationRead(notification: NotificationItem): Result<Unit> {
        items.value = items.value.filterNot { it.conversationId == notification.conversationId }
        return Result.success(Unit)
    }
    override suspend fun dismissNotification(notification: NotificationItem): Result<Unit> = markNotificationRead(notification)
}

private fun iosNotificationsEvidenceStrings(): NotificationsStrings = NotificationsStrings(
    title = "Avisos",
    subtitle = "Notificaciones push y actividad",
    backContentDescription = "Volver",
    loadingLabel = "Cargando avisos",
    emptyTitle = "A\u00fan no hay avisos",
    emptyMessage = "La actividad nueva aparecer\u00e1 aqu\u00ed.",
    errorTitle = "Los avisos no estan disponibles",
    retryLabel = "Reintentar",
    relativeTime = { _, _ -> "Ahora" },
    localizedBody = { it },
    sosPreviewCatalog = SosPreviewCatalog.Spanish,
    photoPreview = "Foto",
    videoPreview = "Video",
    documentPreview = "Documento",
    voiceNotePreview = "Nota de voz",
    filePreview = "Archivo",
)
