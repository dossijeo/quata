package com.quata.bettermessages

import kotlinx.serialization.json.Json

object BetterMessagesJson {
    val default: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        encodeDefaults = false
        coerceInputValues = true
    }
}
