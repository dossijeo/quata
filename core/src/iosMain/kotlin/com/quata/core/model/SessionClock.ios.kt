package com.quata.core.model

import platform.posix.time

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun currentEpochSeconds(): Long = time(null).toLong()
