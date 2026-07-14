package com.quata.core.network

internal class ForegroundConnectivityReconciler(
    initialAvailable: Boolean,
    initiallyForeground: Boolean
) {
    private var isForeground = initiallyForeground
    var isAvailable: Boolean = initialAvailable
        private set

    fun onBackground() {
        isForeground = false
    }

    fun onForeground(observedAvailable: Boolean): Boolean {
        isForeground = true
        isAvailable = observedAvailable
        return isAvailable
    }

    fun onNetworkObservation(observedAvailable: Boolean): Boolean? {
        if (!isForeground) return null
        isAvailable = observedAvailable
        return isAvailable
    }
}
