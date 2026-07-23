package com.quata.feature.chat.presentation.chat

actual fun currentEpochMillis(): Long = (js("Date.now()") as Double).toLong()
