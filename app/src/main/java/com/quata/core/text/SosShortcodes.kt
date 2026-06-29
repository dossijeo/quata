package com.quata.core.text

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class SosShortcodeKind {
    Alert,
    LocationUpdate
}

data class SosShortcodeMessage(
    val kind: SosShortcodeKind,
    val senderName: String,
    val customMessage: String?,
    val latitude: Double?,
    val longitude: Double?,
    val ageMillis: Long?,
    val accuracyMeters: Double?,
    val speedKmh: Double?
) {
    val hasLocation: Boolean = latitude != null && longitude != null
    val mapsUrl: String? = if (hasLocation) {
        "https://maps.google.com/?q=$latitude,$longitude"
    } else {
        null
    }
}

fun buildSosShortcode(
    kind: SosShortcodeKind,
    senderName: String,
    customMessage: String? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    ageMillis: Long? = null,
    accuracyMeters: Double? = null,
    speedKmh: Double? = null
): String =
    buildString {
        append("[SOS")
        append(":kind=")
        append(if (kind == SosShortcodeKind.LocationUpdate) "update" else "alert")
        append(";name=")
        append(senderName.sosEncode())
        customMessage?.takeIf { it.isNotBlank() }?.let {
            append(";custom=")
            append(it.sosEncode())
        }
        latitude?.let {
            append(";lat=")
            append(it.sosNumber())
        }
        longitude?.let {
            append(";lng=")
            append(it.sosNumber())
        }
        ageMillis?.let {
            append(";age_ms=")
            append(it.coerceAtLeast(0L))
        }
        accuracyMeters?.let {
            append(";accuracy_m=")
            append(it.sosNumber())
        }
        speedKmh?.let {
            append(";speed_kmh=")
            append(it.sosNumber())
        }
        append("]")
    }

fun String.parseSosShortcode(): SosShortcodeMessage? {
    val match = SosShortcodeRegex.find(this) ?: return null
    val values = match.groupValues
        .getOrNull(1)
        .orEmpty()
        .split(';')
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            part.substring(0, separator).trim().lowercase(Locale.ROOT) to part.substring(separator + 1).trim()
        }
        .toMap()
    val kind = when (values["kind"]?.lowercase(Locale.ROOT)) {
        "update", "location_update" -> SosShortcodeKind.LocationUpdate
        else -> SosShortcodeKind.Alert
    }
    val senderName = values["name"]?.sosDecode()?.takeIf { it.isNotBlank() } ?: ""
    return SosShortcodeMessage(
        kind = kind,
        senderName = senderName,
        customMessage = values["custom"]?.sosDecode()?.takeIf { it.isNotBlank() },
        latitude = values["lat"]?.toDoubleOrNull(),
        longitude = values["lng"]?.toDoubleOrNull(),
        ageMillis = values["age_ms"]?.toLongOrNull(),
        accuracyMeters = values["accuracy_m"]?.toDoubleOrNull(),
        speedKmh = values["speed_kmh"]?.toDoubleOrNull()
    )
}

private fun String.sosEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.sosDecode(): String =
    runCatching { URLDecoder.decode(this, StandardCharsets.UTF_8.name()) }.getOrDefault(this)

private fun Double.sosNumber(): String =
    String.format(Locale.ROOT, "%.6f", this).trimEnd('0').trimEnd('.')

private val SosShortcodeRegex = Regex("""\[SOS:([^\]]+)]""", RegexOption.IGNORE_CASE)
