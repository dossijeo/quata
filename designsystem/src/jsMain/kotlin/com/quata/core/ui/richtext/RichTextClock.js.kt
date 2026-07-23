package com.quata.core.ui.richtext

actual fun richTextClockMillis(): Long = (js("Date.now()") as Double).toLong()
