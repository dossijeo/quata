package com.quata.feature.chat.presentation.chat

import platform.posix.time

actual fun currentEpochMillis(): Long = time(null).toLong() * 1_000L
