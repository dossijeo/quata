package com.quata.core.model

import platform.Foundation.NSDate

actual fun currentEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
