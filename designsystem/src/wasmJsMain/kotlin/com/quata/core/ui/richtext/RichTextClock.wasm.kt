package com.quata.core.ui.richtext

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual fun richTextClockMillis(): Long = Clock.System.now().toEpochMilliseconds()
