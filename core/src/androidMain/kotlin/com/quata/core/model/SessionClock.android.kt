package com.quata.core.model

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L
