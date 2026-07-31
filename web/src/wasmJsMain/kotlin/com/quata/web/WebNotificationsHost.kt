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
) {
    // Keep the input while this public API remains consumed by Main. The configured state is
    // deliberately not rendered as a permanent banner: Web Push belongs to web_login/logout.
    @Suppress("UNUSED_VARIABLE") val backendConfigured = runtimeConfiguration.isBackendConfigured
    var nowMillis by remember { mutableLongStateOf(notificationsBrowserNowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
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
            loadingLabel = "Cargando avisos…",
            emptyTitle = "Aún no hay avisos",
            emptyMessage = "La actividad nueva aparecerá aquí.",
            errorTitle = "Los avisos no están disponibles",
            relativeTime = { createdAt, now ->
                notificationRelativeTimeLabel(createdAt, now, SpanishNotificationRelativeTimeStrings)
            },
            localizedBody = { it },
            photoPreview = "🖼️ Foto",
            videoPreview = "🎥 Vídeo",
            documentPreview = "📄 Documento",
            voiceNotePreview = "🎤 Nota de voz",
            filePreview = "📎 Archivo",
        ),
        // The normal Android hierarchy has no delivery-status header. Registration is handled
        // by the established web_login/web_logout path, and only an actionable state may add one.
        deliveryNotice = null,
        onBack = onBack,
        onOpenConversation = onOpenConversation,
        canMutate = canMutate,
        onAuthenticationRequired = { item -> onAuthenticationRequired(item.conversationId) },
        // Preserve the current browser contract: both anonymous interactions retain the
        // conversation target while the common auth prompt is shown.
        onDismissAuthenticationRequired = { item -> onAuthenticationRequired(item.conversationId) },
    )
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
    oneYearAgo = "hace 1 año",
    yearsAgo = { "hace $it años" },
)

internal const val webNotificationsActivitySubtitle = "Notificaciones push y actividad"

@JsFun("() => Date.now()")
private external fun notificationsBrowserNowMillisAsDouble(): Double

/** Date.now() is a JavaScript Number, not the BigInt required by a Wasm Kotlin Long. */
internal fun notificationsBrowserNowMillis(): Long = notificationsBrowserNowMillisAsDouble().toLong()
