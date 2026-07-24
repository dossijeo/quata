package com.quata.core.ui.richtext

import platform.posix.time

actual fun richTextClockMillis(): Long = time(null).toLong() * 1_000L
