package com.quata.core.text

/** Short, safe SOS text for notification surfaces; transport shortcodes stay hidden. */
data class SosPreviewCatalog(
    val locationUpdate: String,
    val locationUnavailable: String,
    val locationPermissionDenied: String,
    val locationTimedOut: String,
    val locationFailed: String,
    val approximateLocation: (String) -> String,
) {
    companion object {
        val Spanish = SosPreviewCatalog(
            locationUpdate = "Actualizacion de ubicacion SOS",
            locationUnavailable = "📍 Ubicación no disponible",
            locationPermissionDenied = "📍 Ubicación no disponible: permiso denegado",
            locationTimedOut = "📍 Ubicación no disponible: tiempo agotado",
            locationFailed = "📍 Ubicación no disponible: error al obtenerla",
            approximateLocation = { "Ubicacion aproximada: $it" },
        )
        val English = SosPreviewCatalog(
            "SOS location update",
            "📍 Location unavailable",
            "📍 Location unavailable: permission denied",
            "📍 Location unavailable: timed out",
            "📍 Location unavailable: location failed",
        ) { "Location (approximate): $it" }
        val French = SosPreviewCatalog(
            "Mise a jour de position SOS",
            "📍 Position indisponible",
            "📍 Position indisponible : autorisation refusee",
            "📍 Position indisponible : delai depasse",
            "📍 Position indisponible : erreur de localisation",
        ) { "Position approximative : $it" }
        fun forLanguage(language: String?): SosPreviewCatalog = when (language?.lowercase()) {
            "en" -> English
            "fr" -> French
            else -> Spanish
        }
    }
}

fun resolveLocalizedSosPreview(raw: String, catalog: SosPreviewCatalog): String? {
    val message = raw.parseSosShortcode() ?: return null
    return when {
        message.kind == SosShortcodeKind.LocationUpdate -> catalog.locationUpdate
        !message.hasLocation -> catalog.unavailableLabel(message.locationUnavailableReason)
        else -> catalog.approximateLocation(message.mapsUrl.orEmpty())
    }
}

private fun SosPreviewCatalog.unavailableLabel(reason: SosLocationUnavailableReason?): String =
    when (reason) {
        SosLocationUnavailableReason.PermissionDenied -> locationPermissionDenied
        SosLocationUnavailableReason.Timeout -> locationTimedOut
        SosLocationUnavailableReason.Failed -> locationFailed
        SosLocationUnavailableReason.Unavailable,
        null -> locationUnavailable
    }
