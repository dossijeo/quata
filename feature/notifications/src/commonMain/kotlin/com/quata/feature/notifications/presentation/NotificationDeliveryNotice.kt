package com.quata.feature.notifications.presentation

/**
 * Honest delivery state for platform notification adapters. It does not represent inbox data,
 * a provider token, permission result, or evidence that a push was delivered.
 */
enum class NotificationDeliveryState {
    NotConfigured,
    PermissionRequired,
    DeliveryUnverified,
}

data class NotificationDeliveryNotice(
    val state: NotificationDeliveryState,
    val title: String,
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

/**
 * Common, deliberately conservative Spanish copy. A launcher can replace the text while keeping
 * the state and optional action portable.
 */
fun notificationDeliveryNotice(
    state: NotificationDeliveryState,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
): NotificationDeliveryNotice = when (state) {
    NotificationDeliveryState.NotConfigured -> NotificationDeliveryNotice(
        state = state,
        title = "Notificaciones push sin configurar",
        message = "La bandeja puede mostrar mensajes existentes, pero este despliegue no puede registrar ni confirmar entrega push.",
        actionLabel = actionLabel,
        onAction = onAction,
    )
    NotificationDeliveryState.PermissionRequired -> NotificationDeliveryNotice(
        state = state,
        title = "Permiso de notificaciones pendiente",
        message = "Permite las notificaciones en el sistema para que esta aplicación pueda solicitar alertas; la entrega push aún no está verificada.",
        actionLabel = actionLabel,
        onAction = onAction,
    )
    NotificationDeliveryState.DeliveryUnverified -> NotificationDeliveryNotice(
        state = state,
        title = "Entrega push sin verificar",
        message = "La integración está compuesta, pero no se ha verificado una entrega real en este dispositivo.",
        actionLabel = actionLabel,
        onAction = onAction,
    )
}
