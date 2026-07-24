package com.quata.core.ui.richtext

import platform.posix.time

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun richTextClockMillis(): Long = time(null).toLong() * 1_000L
