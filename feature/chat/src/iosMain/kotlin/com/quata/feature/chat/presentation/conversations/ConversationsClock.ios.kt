package com.quata.feature.chat.presentation.conversations

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSISO8601DateFormatWithInternetDateTime
import platform.Foundation.NSISO8601DateFormatWithFractionalSeconds

internal actual fun conversationsCurrentTimeMillis(): Long = millisFromDate(NSDate())

internal actual fun parseConversationUpdatedAtMillis(value: String, nowMillis: Long): Long? {
    val normalized = value.trim()
    if (normalized.equals("Ahora", true) || normalized.equals("Now", true) || normalized.equals("Maintenant", true)) return nowMillis
    if (normalized.equals("Ayer", true) || normalized.equals("Yesterday", true) || normalized.equals("Hier", true)) return nowMillis - 86_400_000L
    return normalized.toLongOrNull() ?: NSISO8601DateFormatter().run {
        formatOptions = NSISO8601DateFormatWithInternetDateTime or NSISO8601DateFormatWithFractionalSeconds
        dateFromString(normalized)?.let(::millisFromDate)
    } ?: NSISO8601DateFormatter().run {
        formatOptions = NSISO8601DateFormatWithInternetDateTime
        dateFromString(normalized)?.let(::millisFromDate)
    } ?: NSDateFormatter().run {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd'T'HH:mm:ssXXXXX"
        dateFromString(normalized)?.let(::millisFromDate)
    } ?: NSDateFormatter().run {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
        dateFromString(normalized)?.let(::millisFromDate)
    }
}

private fun millisFromDate(date: NSDate): Long = ((date.timeIntervalSinceReferenceDate + 978_307_200.0) * 1000.0).toLong()
