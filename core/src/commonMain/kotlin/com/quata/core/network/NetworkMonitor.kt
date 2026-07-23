package com.quata.core.network

import kotlinx.coroutines.flow.StateFlow

/** Platform-specific connectivity observation exposed to shared repositories. */
interface NetworkMonitor {
    val isAvailable: StateFlow<Boolean>
}
