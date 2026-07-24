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
