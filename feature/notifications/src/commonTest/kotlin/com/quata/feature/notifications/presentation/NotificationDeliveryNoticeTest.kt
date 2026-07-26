package com.quata.feature.notifications.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationDeliveryNoticeTest {
    @Test
    fun notConfiguredCopyDoesNotClaimPushDelivery() {
        val notice = notificationDeliveryNotice(NotificationDeliveryState.NotConfigured)

        assertEquals(NotificationDeliveryState.NotConfigured, notice.state)
        assertTrue(notice.title.contains("sin configurar"))
        assertTrue(notice.message.contains("no puede registrar ni confirmar entrega push"))
        assertNull(notice.actionLabel)
        assertNull(notice.onAction)
    }

    @Test
    fun permissionActionRemainsInjectedAtThePlatformBoundary() {
        var requested = false
        val notice = notificationDeliveryNotice(
            state = NotificationDeliveryState.PermissionRequired,
            actionLabel = "Permitir",
            onAction = { requested = true },
        )

        assertEquals("Permitir", notice.actionLabel)
        notice.onAction?.invoke()
        assertTrue(requested)
        assertFalse(notice.message.contains("está habilitada"))
    }
}
