package com.quata.core.text

import kotlin.math.abs
import kotlin.math.roundToLong

enum class SosShortcodeKind {
    Alert,
    LocationUpdate,
}

data class SosShortcodeMessage(
    val kind: SosShortcodeKind,
    val senderName: String,
    val customMessage: String?,
    val latitude: Double?,
    val longitude: Double?,
    val ageMillis: Long?,
    val accuracyMeters: Double?,
    val speedKmh: Double?,
) {
    val hasLocation: Boolean = latitude != null && longitude != null
    val mapsUrl: String? = if (hasLocation) "https://maps.google.com/?q=$latitude,$longitude" else null
}

fun buildSosShortcode(
    kind: SosShortcodeKind,
    senderName: String,
    customMessage: String? = null,
    latitude: Double? = null,
    longitude: Double? = null,
    ageMillis: Long? = null,
    accuracyMeters: Double? = null,
    speedKmh: Double? = null,
): String = buildString {
    append("[SOS:kind=")
    append(if (kind == SosShortcodeKind.LocationUpdate) "update" else "alert")
    append(";name=")
    append(senderName.sosEncode())
    customMessage?.takeIf { it.isNotBlank() }?.let { append(";custom=").append(it.sosEncode()) }
    latitude?.let { append(";lat=").append(it.sosNumber()) }
    longitude?.let { append(";lng=").append(it.sosNumber()) }
    ageMillis?.let { append(";age_ms=").append(it.coerceAtLeast(0L)) }
    accuracyMeters?.let { append(";accuracy_m=").append(it.sosNumber()) }
    speedKmh?.let { append(";speed_kmh=").append(it.sosNumber()) }
    append(']')
}

fun String.parseSosShortcode(): SosShortcodeMessage? {
    val values = SosShortcodeRegex.find(this)?.groupValues?.getOrNull(1)
        ?.split(';')
        ?.mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) null else part.substring(0, separator).trim().lowercase() to part.substring(separator + 1).trim()
        }
        ?.toMap()
        ?: return null
    val kind = when (values["kind"]?.lowercase()) {
        "update", "location_update" -> SosShortcodeKind.LocationUpdate
        else -> SosShortcodeKind.Alert
    }
    return SosShortcodeMessage(
        kind = kind,
        senderName = values["name"]?.sosDecode()?.takeIf { it.isNotBlank() }.orEmpty(),
        customMessage = values["custom"]?.sosDecode()?.takeIf { it.isNotBlank() },
        latitude = values["lat"]?.toDoubleOrNull(),
        longitude = values["lng"]?.toDoubleOrNull(),
        ageMillis = values["age_ms"]?.toLongOrNull(),
        accuracyMeters = values["accuracy_m"]?.toDoubleOrNull(),
        speedKmh = values["speed_kmh"]?.toDoubleOrNull(),
    )
}

private fun String.sosEncode(): String = buildString {
    this@sosEncode.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xFF
        if ((code in 'a'.code..'z'.code) || (code in 'A'.code..'Z'.code) || (code in '0'.code..'9'.code) || code in intArrayOf('-'.code, '_'.code, '.'.code, '~'.code)) append(code.toChar())
        else append('%').append("0123456789ABCDEF"[code shr 4]).append("0123456789ABCDEF"[code and 0xF])
    }
}

private fun String.sosDecode(): String = runCatching {
    buildString {
        var index = 0
        while (index < this@sosDecode.length) {
            if (this@sosDecode[index] != '%') append(this@sosDecode[index++])
            else {
                val bytes = ArrayList<Byte>()
                while (index + 2 < this@sosDecode.length && this@sosDecode[index] == '%') {
                    val high = this@sosDecode[index + 1].digitToIntOrNull(16) ?: break
                    val low = this@sosDecode[index + 2].digitToIntOrNull(16) ?: break
                    bytes += ((high shl 4) or low).toByte()
                    index += 3
                }
                if (bytes.isEmpty()) append(this@sosDecode[index++]) else append(bytes.toByteArray().decodeToString())
            }
        }
    }
}.getOrDefault(this)

private fun Double.sosNumber(): String {
    val scaled = (this * 1_000_000).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val absolute = abs(scaled)
    val whole = absolute / 1_000_000
    val decimals = (absolute % 1_000_000).toString().padStart(6, '0').trimEnd('0')
    return if (decimals.isEmpty()) "$sign$whole" else "$sign$whole.$decimals"
}

private val SosShortcodeRegex = Regex("""\[SOS:([^\]]+)]""", RegexOption.IGNORE_CASE)
