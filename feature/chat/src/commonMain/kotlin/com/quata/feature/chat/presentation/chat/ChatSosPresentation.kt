package com.quata.feature.chat.presentation.chat

import com.quata.core.text.SosShortcodeKind
import com.quata.core.text.SosLocationUnavailableReason
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
    val unavailableLabel: String,
)

data class ChatSosStrings(
    val userFallback: String,
    val defaultAlert: (String) -> String,
    val locationUpdate: String,
    val locationUpdatedBody: String,
    val location: (String) -> String,
    val approximateLocation: (String) -> String,
    val locationAge: (String) -> String,
    val locationAccuracy: (String) -> String,
    val locationSpeed: (String) -> String,
    val ageLessThanMinute: String,
    val distanceMeters: (String) -> String,
    val speedKmh: (String) -> String,
    val locationUnavailable: String,
    val locationPermissionDenied: String,
    val locationTimedOut: String,
    val locationFailed: String,
    val openMaps: String,
)

fun chatSosStringsForLanguage(languageTag: String?): ChatSosStrings =
    when (languageTag?.substringBefore('-')?.substringBefore('_')?.lowercase()) {
        "es" -> ChatSosStrings(
            userFallback = "Usuario",
            defaultAlert = { sender -> "🚨 SOS REAL: $sender necesita ayuda urgente. Eres uno de sus contactos de emergencia en QUATA." },
            locationUpdate = "Actualizacion de ubicacion SOS",
            locationUpdatedBody = "Se ha obtenido una ubicacion mas precisa.",
            location = { url -> "📍 Ubicación: $url" },
            approximateLocation = { url -> "Ubicacion aproximada: $url" },
            locationAge = { value -> "Antiguedad de ubicacion: $value" },
            locationAccuracy = { value -> "Precision: $value" },
            locationSpeed = { value -> "Velocidad: $value" },
            ageLessThanMinute = "menos de 1 minuto",
            distanceMeters = { value -> "$value m" },
            speedKmh = { value -> "$value km/h" },
            locationUnavailable = "📍 Ubicación no disponible",
            locationPermissionDenied = "📍 Ubicación no disponible: permiso denegado",
            locationTimedOut = "📍 Ubicación no disponible: tiempo agotado",
            locationFailed = "📍 Ubicación no disponible: error al obtenerla",
            openMaps = "Abrir ubicación en Google Maps",
        )
        "fr" -> ChatSosStrings(
            userFallback = "Utilisateur",
            defaultAlert = { sender -> "🚨 SOS REEL : $sender a besoin d'aide urgente. Tu es un de ses contacts SOS sur QUATA." },
            locationUpdate = "Mise a jour de position SOS",
            locationUpdatedBody = "Une position plus precise a ete obtenue.",
            location = { url -> "📍 Position : $url" },
            approximateLocation = { url -> "Position approximative : $url" },
            locationAge = { value -> "Age de la position : $value" },
            locationAccuracy = { value -> "Precision : $value" },
            locationSpeed = { value -> "Vitesse : $value" },
            ageLessThanMinute = "moins d'1 minute",
            distanceMeters = { value -> "$value m" },
            speedKmh = { value -> "$value km/h" },
            locationUnavailable = "📍 Position indisponible",
            locationPermissionDenied = "📍 Position indisponible : autorisation refusee",
            locationTimedOut = "📍 Position indisponible : delai depasse",
            locationFailed = "📍 Position indisponible : erreur de localisation",
            openMaps = "Ouvrir la position dans Google Maps",
        )
        else -> ChatSosStrings(
            userFallback = "User",
            defaultAlert = { sender -> "🚨 REAL SOS: $sender needs urgent help. You are one of their emergency contacts in QUATA." },
            locationUpdate = "SOS location update",
            locationUpdatedBody = "A more precise location has been obtained.",
            location = { url -> "📍 Location: $url" },
            approximateLocation = { url -> "Location (approximate): $url" },
            locationAge = { value -> "Location age: $value" },
            locationAccuracy = { value -> "Accuracy: $value" },
            locationSpeed = { value -> "Speed: $value" },
            ageLessThanMinute = "less than 1 minute",
            distanceMeters = { value -> "$value m" },
            speedKmh = { value -> "$value km/h" },
            locationUnavailable = "📍 Location unavailable",
            locationPermissionDenied = "📍 Location unavailable: permission denied",
            locationTimedOut = "📍 Location unavailable: timed out",
            locationFailed = "📍 Location unavailable: location failed",
            openMaps = "Open location in Google Maps",
        )
    }

fun resolveChatSosPresentation(raw: String, strings: ChatSosStrings): ChatSosPresentation? {
    raw.parseSosShortcode()?.let { message ->
        val sender = message.senderName.ifBlank { strings.userFallback }
        val update = message.kind == SosShortcodeKind.LocationUpdate
        return ChatSosPresentation(
            title = if (update) strings.locationUpdate else message.customMessage ?: strings.defaultAlert(sender),
            body = strings.locationUpdatedBody.takeIf { update },
            locationLabel = message.mapsUrl?.let { if (update) strings.location(it) else strings.approximateLocation(it) },
            mapsUrl = message.mapsUrl,
            age = message.ageMillis?.let { strings.locationAge(formatSosAge(it, strings)) },
            accuracy = message.accuracyMeters?.let { strings.locationAccuracy(strings.distanceMeters(it.roundToLong().toString())) },
            speed = message.speedKmh?.let { strings.locationSpeed(strings.speedKmh(formatOneDecimal(it))) },
            isUpdate = update,
            isUnavailable = !message.hasLocation,
            unavailableLabel = strings.unavailableLabel(message.locationUnavailableReason),
        )
    }
    return resolveLegacyChatSosPresentation(raw, strings)
}

private fun resolveLegacyChatSosPresentation(raw: String, strings: ChatSosStrings): ChatSosPresentation? {
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
        locationLabel = mapsUrl?.let(strings.location),
        mapsUrl = mapsUrl,
        age = lines.extractSosValue("location age", "antigüedad", "antiguedad", "âge", "age de la position")?.let(strings.locationAge),
        accuracy = lines.extractSosValue("accuracy", "precisión", "precision")?.let(strings.locationAccuracy),
        speed = lines.extractSosValue("speed", "velocidad", "vitesse")?.let(strings.locationSpeed),
        isUpdate = update,
        isUnavailable = mapsUrl == null,
        unavailableLabel = strings.locationUnavailable,
    )
}

private fun ChatSosStrings.unavailableLabel(reason: SosLocationUnavailableReason?): String =
    when (reason) {
        SosLocationUnavailableReason.PermissionDenied -> locationPermissionDenied
        SosLocationUnavailableReason.Timeout -> locationTimedOut
        SosLocationUnavailableReason.Failed -> locationFailed
        SosLocationUnavailableReason.Unavailable,
        null -> locationUnavailable
    }

private fun formatSosAge(milliseconds: Long, strings: ChatSosStrings): String {
    val minutes = milliseconds.coerceAtLeast(0L) / 60_000L
    return when {
        minutes < 1L -> strings.ageLessThanMinute
        minutes < 60L -> "$minutes min"
        minutes < 1_440L -> "${minutes / 60L} h"
        else -> "${minutes / 1_440L} d"
    }
}

private fun formatOneDecimal(value: Double): String {
    val scaled = (value * 10.0).roundToLong()
    return "${scaled / 10}.${kotlin.math.abs(scaled % 10)}"
}

private fun List<String>.extractSosValue(vararg labels: String): String? =
    firstOrNull { line -> line.startsWithAnySosLabel(*labels) }
        ?.substringAfter(':', missingDelimiterValue = "")
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun String.startsWithAnySosLabel(vararg labels: String): Boolean {
    val normalized = trim().lowercase()
    return labels.any { label ->
        normalized.startsWith("${label.lowercase()}:") || normalized.startsWith("${label.lowercase()} :")
    }
}
