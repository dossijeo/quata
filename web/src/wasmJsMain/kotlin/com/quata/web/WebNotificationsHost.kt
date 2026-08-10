@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.quata.feature.notifications.presentation.NotificationRelativeTimeStrings
import com.quata.feature.notifications.presentation.NotificationsHostContent
import com.quata.feature.notifications.presentation.NotificationsStrings
import com.quata.feature.notifications.presentation.notificationRelativeTimeLabel
import com.quata.core.text.SosPreviewCatalog
import kotlinx.coroutines.delay

/** Browser adapter only: the product screen remains [NotificationsHostContent]. */
@Composable
fun WebNotificationsHost(
    repository: WebNotificationsRepository,
    runtimeConfiguration: WebRuntimeConfiguration,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    canMutate: Boolean,
    onAuthenticationRequired: (String) -> Unit,
    onDismissAuthenticationRequired: () -> Unit,
) {
    // Keep the input while this public API remains consumed by Main. The configured state is
    // deliberately not rendered as a permanent banner: Web Push belongs to web_login/logout.
    @Suppress("UNUSED_VARIABLE") val backendConfigured = runtimeConfiguration.isBackendConfigured
    val authenticationPolicy = remember(onAuthenticationRequired, onDismissAuthenticationRequired) {
        WebNotificationAuthenticationPolicy(
            onConversationAuthenticationRequired = onAuthenticationRequired,
            onDismissAuthenticationRequired = onDismissAuthenticationRequired,
        )
    }
    var nowMillis by remember { mutableLongStateOf(notificationsBrowserNowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMillis = notificationsBrowserNowMillis()
        }
    }
    NotificationsHostContent(
        padding = PaddingValues(),
        repository = repository,
        timestampNowMillis = nowMillis,
        strings = NotificationsStrings(
            title = "Avisos",
            subtitle = webNotificationsActivitySubtitle,
            backContentDescription = "Volver",
            loadingLabel = "Cargando avisos\u2026",
            emptyTitle = "A\u00fan no hay avisos",
            emptyMessage = "La actividad nueva aparecer\u00e1 aqu\u00ed.",
            errorTitle = "Los avisos no est\u00e1n disponibles",
            retryLabel = "Reintentar",
            relativeTime = { createdAt, now ->
                notificationRelativeTimeLabel(createdAt, now, SpanishNotificationRelativeTimeStrings)
            },
            localizedBody = { it },
            sosPreviewCatalog = SosPreviewCatalog.Spanish,
            photoPreview = "\uD83D\uDDBC\uFE0F Foto",
            videoPreview = "\uD83C\uDFA5 V\u00eddeo",
            documentPreview = "\uD83D\uDCC4 Documento",
            voiceNotePreview = "\uD83C\uDFA4 Nota de voz",
            filePreview = "\uD83D\uDCCE Archivo",
        ),
        // The normal Android hierarchy has no delivery-status header. Registration is handled
        // by the established web_login/web_logout path, and only an actionable state may add one.
        deliveryNotice = null,
        onBack = onBack,
        onOpenConversation = onOpenConversation,
        canMutate = canMutate,
        onAuthenticationRequired = { item -> authenticationPolicy.requestForClick(item.conversationId) },
        // Swiping is a blocked mutation, not a request to navigate into the conversation.
        // Keep Notifications visible behind the participation prompt without queuing chat.
        onDismissAuthenticationRequired = { authenticationPolicy.requestForDismiss() },
    )
}

internal class WebNotificationAuthenticationPolicy(
    private val onConversationAuthenticationRequired: (String) -> Unit,
    private val onDismissAuthenticationRequired: () -> Unit,
) {
    fun requestForClick(conversationId: String) {
        onConversationAuthenticationRequired(conversationId)
    }

    fun requestForDismiss() {
        onDismissAuthenticationRequired()
    }
}

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
    oneYearAgo = "hace 1 a\u00f1o",
    yearsAgo = { "hace $it a\u00f1os" },
)

internal const val webNotificationsActivitySubtitle = "Notificaciones push y actividad"

@JsFun("() => Date.now()")
private external fun notificationsBrowserNowMillisAsDouble(): Double

/** Date.now() is a JavaScript Number, not the BigInt required by a Wasm Kotlin Long. */
internal fun notificationsBrowserNowMillis(): Long = notificationsBrowserNowMillisAsDouble().toLong()
