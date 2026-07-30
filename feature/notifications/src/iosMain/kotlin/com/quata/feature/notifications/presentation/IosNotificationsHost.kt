package com.quata.feature.notifications.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.notifications.domain.NotificationsRepository
import platform.UIKit.UIViewController

/** Swift supplies data, deep-link navigation and permission presentation; no push provider lives here. */
class IosNotificationsHostDependencies(
    val repository: NotificationsRepository,
    val strings: NotificationsStrings,
    val deliveryNotice: NotificationDeliveryNotice?,
    val onBack: () -> Unit,
    val onOpenConversation: (String) -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onHandleDeepLink: (String) -> Unit,
)

/**
 * Creates the standard in-app inbox strings and dependencies at the iOS boundary.
 * The inbox remains backed by the supplied authenticated repository; these strings do not
 * manufacture notification data or a push provider.
 */
fun createIosNotificationsHostDependencies(
    repository: NotificationsRepository,
    notificationPermissionGranted: Boolean,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onHandleDeepLink: (String) -> Unit,
): IosNotificationsHostDependencies = IosNotificationsHostDependencies(
    repository = repository,
    strings = NotificationsStrings(
        title = "Notificaciones",
        subtitle = "Mensajes no leídos",
        backContentDescription = "Volver",
        relativeTimeCatalog = RelativeTimeCatalog(
            seconds = { "hace $it s" }, oneMinute = "hace 1 min", minutes = { "hace $it min" }, hours = { "hace $it h" },
            days = { "hace $it d" }, oneWeek = "hace 1 semana", weeks = { "hace $it semanas" }, oneMonth = "hace 1 mes",
            months = { "hace $it meses" }, oneYear = "hace 1 año", years = { "hace $it años" },
        ),
        previewCatalog = ChatPreviewCatalog("🖼️ Foto", "🎥 Vídeo", "📄 Documento", "🎤 Nota de voz", "📎 Archivo"),
        sosPreviewCatalog = SosPreviewCatalog(
            locationUpdate = "Actualizacion de ubicacion SOS",
            locationUnavailable = "📍 Ubicación no disponible",
            approximateLocation = { "Ubicacion aproximada: $it" },
        ),
    ),
    deliveryNotice = if (notificationPermissionGranted) null else notificationDeliveryNotice(
        state = NotificationDeliveryState.PermissionRequired,
        actionLabel = "Permitir notificaciones",
        onAction = onRequestNotificationPermission,
    ),
    onBack = onBack,
    onOpenConversation = onOpenConversation,
    onRequestNotificationPermission = onRequestNotificationPermission,
    onHandleDeepLink = onHandleDeepLink,
)

fun QuataNotificationsViewController(dependencies: IosNotificationsHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme {
        NotificationsHostContent(
            padding = PaddingValues(),
            repository = dependencies.repository,
            strings = dependencies.strings,
            deliveryNotice = dependencies.deliveryNotice,
            onBack = dependencies.onBack,
            onOpenConversation = { id ->
                dependencies.onHandleDeepLink(id)
                dependencies.onOpenConversation(id)
            },
        )
    }
}
