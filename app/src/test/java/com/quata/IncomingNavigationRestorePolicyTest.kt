package com.quata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingNavigationRestorePolicyTest {
    @Test
    fun pendingDeepLinkIsDeliveredAcrossRecreation() {
        assertTrue(
            IncomingNavigationRestorePolicy.shouldDeliver(
                isNavigationViewIntent = true,
                consumedBeforeRecreation = false,
            ),
        )
    }

    @Test
    fun consumedDeepLinkIsNotReplayedAcrossRecreation() {
        assertFalse(
            IncomingNavigationRestorePolicy.shouldDeliver(
                isNavigationViewIntent = true,
                consumedBeforeRecreation = true,
            ),
        )
    }
}
