package com.quata.core.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual fun currentEpochSeconds(): Long = Clock.System.now().epochSeconds
