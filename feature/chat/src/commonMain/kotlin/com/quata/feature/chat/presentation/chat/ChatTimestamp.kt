package com.quata.feature.chat.presentation.chat

import com.quata.core.model.Message
import com.quata.feature.chat.presentation.conversations.conversationsLocaleCatalogForLanguage
import com.quata.feature.chat.presentation.conversations.localizedRelativeConversationTime
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Android-compatible Chat timestamp used by the shared message and Fang surfaces. */
@OptIn(ExperimentalTime::class)
fun chatMessageTimestampLabel(
    message: Message,
    languageTag: String?,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val millis = message.sentAtMillis ?: runCatching { Instant.parse(message.sentAt).toEpochMilliseconds() }.getOrNull()
        ?: return message.sentAt
    val elapsed = (nowMillis - millis).coerceAtLeast(0L)
    if (elapsed < DAY_MILLIS) {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone)
        return "${local.hour.twoDigits()}:${local.minute.twoDigits()}"
    }
    return localizedRelativeConversationTime(
        ageMillis = elapsed,
        strings = conversationsLocaleCatalogForLanguage(languageTag).relativeTime,
    )
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
private fun Int.twoDigits(): String = toString().padStart(2, '0')
