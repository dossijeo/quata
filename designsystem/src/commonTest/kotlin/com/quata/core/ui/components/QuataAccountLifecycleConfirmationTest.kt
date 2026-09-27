package com.quata.core.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuataAccountLifecycleConfirmationTest {
    @Test
    fun deactivationRequiresOnlyANonBlankPassword() {
        assertFalse(accountLifecycleConfirmationEnabled("", "", null, isWorking = false))
        assertTrue(accountLifecycleConfirmationEnabled("correct horse", "", null, isWorking = false))
    }

    @Test
    fun deletionRequiresTheExactConfirmationIgnoringCaseAndOuterWhitespace() {
        assertFalse(accountLifecycleConfirmationEnabled("correct horse", "", "ELIMINAR", isWorking = false))
        assertFalse(accountLifecycleConfirmationEnabled("correct horse", "ELIMINA", "ELIMINAR", isWorking = false))
        assertTrue(accountLifecycleConfirmationEnabled("correct horse", "  eliminar  ", "ELIMINAR", isWorking = false))
    }

    @Test
    fun pendingOperationDisablesEveryDestructiveConfirmation() {
        assertFalse(accountLifecycleConfirmationEnabled("correct horse", "ELIMINAR", "ELIMINAR", isWorking = true))
        assertFalse(accountLifecycleConfirmationEnabled("correct horse", "", null, isWorking = true))
    }
}
