package com.quata.core.presence

import androidx.compose.runtime.staticCompositionLocalOf

val LocalUserPresence = staticCompositionLocalOf<UserPresenceRepository?> { null }
