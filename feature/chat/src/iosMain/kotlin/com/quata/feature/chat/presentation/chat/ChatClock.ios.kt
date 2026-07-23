package com.quata.feature.chat.presentation.chat

import platform.Foundation.NSDate

actual fun currentEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
