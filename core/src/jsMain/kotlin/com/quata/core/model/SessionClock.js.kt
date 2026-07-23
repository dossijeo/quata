package com.quata.core.model

actual fun currentEpochSeconds(): Long = (js("Date.now()") as Double / 1000.0).toLong()
