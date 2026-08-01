package com.quata.feature.notifications.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.model.NotificationItem
import com.quata.core.text.SosPreviewCatalog
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.feature.notifications.domain.NotificationsRepository
import kotlinx.coroutines.delay
import platform.UIKit.UIViewController
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Swift supplies platform services and navigation only; the inbox UI remains common Compose. */
class IosNotificationsHostDependencies(
    val repository: NotificationsRepository,
    val timestampNowMillis: Long,
    val strings: NotificationsStrings,
    val deliveryNotice: NotificationDeliveryNotice?,
    val onBack: () -> Unit,
    val onOpenConversation: (String) -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onHandleDeepLink: (String) -> Unit,
    val canMutate: Boolean,
    val onAuthenticationRequired: (NotificationItem) -> Unit,
    val onDismissAuthenticationRequired: (NotificationItem) -> Unit,
)

fun createIosNotificationsHostDependencies(
    repository: NotificationsRepository,
    timestampNowMillis: Long,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onHandleDeepLink: (String) -> Unit,
    canMutate: Boolean = true,
    onAuthenticationRequired: (NotificationItem) -> Unit = {},
    onDismissAuthenticationRequired: (NotificationItem) -> Unit = onAuthenticationRequired,
): IosNotificationsHostDependencies = IosNotificationsHostDependencies(
    repository = repository,
    timestampNowMillis = timestampNowMillis,
    strings = NotificationsStrings(
        title = "Avisos",
        subtitle = "Notificaciones push y actividad",
        backContentDescription = "Volver",
        loadingLabel = "Cargando avisos…",
        emptyTitle = "Aún no hay avisos",
        emptyMessage = "La actividad nueva aparecerá aquí.",
        errorTitle = "Los avisos no están disponibles",
        retryLabel = "Reintentar",
        relativeTime = { createdAt, now ->
            notificationRelativeTimeLabel(createdAt, now, SpanishNotificationRelativeTimeStrings)
        },
        localizedBody = { it },
        sosPreviewCatalog = SosPreviewCatalog.Spanish,
        photoPreview = "🖼️ Foto",
        videoPreview = "🎥 Vídeo",
        documentPreview = "📄 Documento",
        voiceNotePreview = "🎤 Nota de voz",
        filePreview = "📎 Archivo",
    ),
    // APNs permission is a platform capability. Do not add an always-visible delivery banner
    // that Android does not have; the UIKit adapter may surface an actionable prompt when needed.
    deliveryNotice = null,
    onBack = onBack,
    onOpenConversation = onOpenConversation,
    onRequestNotificationPermission = onRequestNotificationPermission,
    onHandleDeepLink = onHandleDeepLink,
    canMutate = canMutate,
    onAuthenticationRequired = onAuthenticationRequired,
    onDismissAuthenticationRequired = onDismissAuthenticationRequired,
)

fun QuataNotificationsViewController(dependencies: IosNotificationsHostDependencies): UIViewController = ComposeUIViewController {
    // The old host captured a single startup timestamp, so labels could never age. The Compose
    // controller owns a lightweight minute tick, just like the browser host.
    var nowMillis by remember { mutableLongStateOf(dependencies.timestampNowMillis) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMillis = iosNotificationsNowMillis()
        }
    }
    QuataTheme {
        NotificationsHostContent(
            padding = PaddingValues(),
            repository = dependencies.repository,
            timestampNowMillis = nowMillis,
            strings = dependencies.strings,
            deliveryNotice = dependencies.deliveryNotice,
            onBack = dependencies.onBack,
            onOpenConversation = { id ->
                dependencies.onHandleDeepLink(id)
                dependencies.onOpenConversation(id)
            },
            canMutate = dependencies.canMutate,
            onAuthenticationRequired = dependencies.onAuthenticationRequired,
            onDismissAuthenticationRequired = dependencies.onDismissAuthenticationRequired,
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun iosNotificationsNowMillis(): Long = Clock.System.now().toEpochMilliseconds()

private val SpanishNotificationRelativeTimeStrings = NotificationRelativeTimeStrings(
    now = "Ahora",
    secondsAgo = { "hace $it s" },
    oneMinuteAgo = "hace 1 min",
    minutesAgo = { "hace $it min" },
    hoursAgo = { "hace $it h" },
    daysAgo = { "hace $it d" },
    oneWeekAgo = "hace 1 semana",
    weeksAgo = { "hace $it semanas" },
    oneMonthAgo = "hace 1 mes",
    monthsAgo = { "hace $it meses" },
    oneYearAgo = "hace 1 año",
    yearsAgo = { "hace $it años" },
)
