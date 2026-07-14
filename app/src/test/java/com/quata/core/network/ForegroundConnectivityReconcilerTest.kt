package com.quata.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundConnectivityReconcilerTest {
    @Test
    fun backgroundCallbacksDoNotOverwriteLastVisibleState() {
        val reconciler = ForegroundConnectivityReconciler(
            initialAvailable = true,
            initiallyForeground = true
        )

        reconciler.onBackground()

        assertNull(reconciler.onNetworkObservation(false))
        assertTrue(reconciler.isAvailable)
    }

    @Test
    fun foregroundReturnAlwaysReconcilesWithCurrentNetwork() {
        val reconciler = ForegroundConnectivityReconciler(
            initialAvailable = false,
            initiallyForeground = false
        )

        assertTrue(reconciler.onForeground(observedAvailable = true))
        assertTrue(reconciler.isAvailable)
    }

    @Test
    fun foregroundReturnStillReportsRealOfflineState() {
        val reconciler = ForegroundConnectivityReconciler(
            initialAvailable = true,
            initiallyForeground = false
        )

        assertFalse(reconciler.onForeground(observedAvailable = false))
        assertFalse(reconciler.isAvailable)
    }

    @Test
    fun visibleNetworkCallbacksRemainImmediate() {
        val reconciler = ForegroundConnectivityReconciler(
            initialAvailable = true,
            initiallyForeground = true
        )

        assertFalse(reconciler.onNetworkObservation(false)!!)
        assertTrue(reconciler.onNetworkObservation(true)!!)
    }
}
