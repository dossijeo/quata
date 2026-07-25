package com.quata.feature.notifications.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.notifications.domain.NotificationsRepository
import platform.UIKit.UIViewController

/** Swift supplies data, deep-link navigation and permission presentation; no push provider lives here. */
class IosNotificationsHostDependencies(
    val repository: NotificationsRepository,
    val timestampNowMillis: Long,
    val strings: NotificationsStrings,
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
    timestampNowMillis: Long,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onHandleDeepLink: (String) -> Unit,
): IosNotificationsHostDependencies = IosNotificationsHostDependencies(
    repository = repository,
    timestampNowMillis = timestampNowMillis,
    strings = NotificationsStrings(
        title = "Notificaciones",
        subtitle = "Mensajes no leídos",
        backContentDescription = "Volver",
        relativeTime = { createdAt, _ -> createdAt.ifBlank { "Ahora" } },
        localizedBody = { it },
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
            timestampNowMillis = dependencies.timestampNowMillis,
            strings = dependencies.strings,
            onBack = dependencies.onBack,
            onOpenConversation = { id ->
                dependencies.onHandleDeepLink(id)
                dependencies.onOpenConversation(id)
            },
        )
    }
}
