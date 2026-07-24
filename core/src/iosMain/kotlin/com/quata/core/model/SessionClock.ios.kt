package com.quata.core.model

import platform.posix.time

actual fun currentEpochSeconds(): Long = time(null).toLong()
