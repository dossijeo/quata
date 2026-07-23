package com.quata.core.ui.richtext

import platform.Foundation.NSDate

actual fun richTextClockMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
