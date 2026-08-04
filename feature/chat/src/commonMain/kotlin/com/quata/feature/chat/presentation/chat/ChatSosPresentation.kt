package com.quata.feature.chat.presentation.chat

import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.parseSosShortcode
import kotlin.math.roundToLong

data class ChatSosPresentation(
    val title: String,
    val body: String?,
    val locationLabel: String?,
    val mapsUrl: String?,
    val age: String?,
    val accuracy: String?,
    val speed: String?,
    val isUpdate: Boolean,
    val isUnavailable: Boolean,
)

fun resolveChatSosPresentation(raw: String): ChatSosPresentation? {
    raw.parseSosShortcode()?.let { message ->
        val sender = message.senderName.ifBlank { "Usuario" }
        val update = message.kind == SosShortcodeKind.LocationUpdate
        return ChatSosPresentation(
            title = if (update) "Actualización de ubicación SOS" else message.customMessage ?: "SOS real de $sender",
            body = if (update) "La ubicación se ha actualizado." else null,
            locationLabel = message.mapsUrl?.let { if (update) "Ubicación: $it" else "Ubicación aproximada: $it" },
            mapsUrl = message.mapsUrl,
            age = message.ageMillis?.let(::formatSosAge),
            accuracy = message.accuracyMeters?.let { "Precisión: ${it.roundToLong()} m" },
            speed = message.speedKmh?.let { "Velocidad: ${formatOneDecimal(it)} km/h" },
            isUpdate = update,
            isUnavailable = !message.hasLocation,
        )
    }
    return resolveLegacyChatSosPresentation(raw)
}

private fun resolveLegacyChatSosPresentation(raw: String): ChatSosPresentation? {
    val lines = raw.lines().map(String::trim).filter(String::isNotBlank)
    val normalized = raw.lowercase()
    val isSos = normalized.contains("sos real") ||
        normalized.contains("sos actual") ||
        normalized.contains("ubicación sos") ||
        normalized.contains("ubicacion sos") ||
        normalized.contains("sos location") ||
        normalized.contains("position sos")
    if (!isSos) return null
    val mapsUrl = Regex("https?://(?:maps\\.google\\.com|www\\.google\\.com/maps|maps\\.apple\\.com)[^\\s]+", RegexOption.IGNORE_CASE)
        .find(raw)?.value
    val update = normalized.contains("actualiz") || normalized.contains("updated") || normalized.contains("mise à jour")
    val metadata = lines.filterNot { line ->
        line == mapsUrl || line.startsWithAnySosLabel(
            "location age", "antigüedad", "antiguedad", "âge", "age de la position",
            "speed", "velocidad", "vitesse", "accuracy", "precisión", "precision",
        )
    }
    return ChatSosPresentation(
        title = metadata.firstOrNull() ?: "SOS",
        body = metadata.drop(1).joinToString("\n").takeIf(String::isNotBlank),
        locationLabel = mapsUrl?.let { "Ubicación: $it" },
        mapsUrl = mapsUrl,
        age = lines.extractSosValue("location age", "antigüedad", "antiguedad", "âge", "age de la position"),
        accuracy = lines.extractSosValue("accuracy", "precisión", "precision"),
        speed = lines.extractSosValue("speed", "velocidad", "vitesse"),
        isUpdate = update,
        isUnavailable = mapsUrl == null,
    )
}

private fun formatSosAge(milliseconds: Long): String {
    val minutes = milliseconds.coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1L -> "Antigüedad: menos de un minuto"
        minutes < 60L -> "Antigüedad: $minutes min"
        minutes < 1_440L -> "Antigüedad: ${minutes / 60L} h"
        else -> "Antigüedad: ${minutes / 1_440L} d"
    }
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10.0).roundToLong()
    return "${scaled / 10}.${kotlin.math.abs(scaled % 10)}"
}

private fun List<String>.extractSosValue(vararg labels: String): String? =
    firstOrNull { line -> line.startsWithAnySosLabel(*labels) }

private fun String.startsWithAnySosLabel(vararg labels: String): Boolean {
    val normalized = trim().lowercase()
    return labels.any { label ->
        normalized.startsWith("${label.lowercase()}:") || normalized.startsWith("${label.lowercase()} :")
    }
}
