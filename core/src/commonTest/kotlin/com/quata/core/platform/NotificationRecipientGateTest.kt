package com.quata.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationRecipientGateTest {
    @Test fun lateInitialFailureCannotReplaceInteractiveLogin() {
        val gate = NotificationRecipientGate()
        val initial = gate.generation
        gate.completeValidation("b")
        gate.completeValidationIfCurrent(initial, null)
        var opened = 0
        gate.receive("b") { opened++ }
        assertEquals(1, opened)
    }

    @Test fun lateInitialSuccessCannotRestoreRecipientAfterLogout() {
        val gate = NotificationRecipientGate()
        val initial = gate.generation
        gate.sessionEnded()
        gate.completeValidationIfCurrent(initial, "a")
        var opened = 0
        gate.receive("a") { opened++ }
        assertEquals(0, opened)
    }

    @Test fun coldTapWaitsForMatchingValidatedSessionAndIsConsumedOnce() {
        val gate = NotificationRecipientGate()
        var opened = 0
        gate.receive("a") { opened++ }
        assertEquals(0, opened)
        gate.completeValidation("a")
        gate.completeValidation("a")
        assertEquals(1, opened)
    }

    @Test fun rejectedRestoreCannotResumeNotificationAfterAnotherLogin() {
        val gate = NotificationRecipientGate()
        var opened = 0
        gate.receive("a") { opened++ }
        gate.completeValidation(null)
        gate.completeValidation("a")
        gate.receive("b") { opened++ }
        assertEquals(0, opened)
    }

    @Test fun logoutAndAccountSwitchDiscardOldRecipients() {
        val gate = NotificationRecipientGate()
        var opened = 0
        gate.completeValidation("a")
        gate.receive("a") { opened++ }
        gate.sessionEnded()
        gate.receive("a") { opened++ }
        gate.completeValidation("b")
        gate.receive("a") { opened++ }
        gate.receive("b") { opened++ }
        assertEquals(2, opened)
    }

    @Test fun legacyUnboundRouteRemainsAvailableButBlankRecipientIsRejected() {
        val gate = NotificationRecipientGate()
        var opened = 0
        gate.receive(null) { opened++ }
        gate.receive(" ") { opened++ }
        gate.completeValidation(null)
        assertEquals(1, opened)
    }
}
