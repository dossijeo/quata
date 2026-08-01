package com.quata.core.text

/**
 * Short, safe SOS text for notification surfaces.  SOS shortcodes are transport
 * data, so no notification UI should expose them verbatim.
 */
data class SosPreviewCatalog(
    val locationUpdate: String,
    val locationUnavailable: String,
    val approximateLocation: (String) -> String,
) {
    companion object {
        val Spanish = SosPreviewCatalog(
            locationUpdate = "Actualizacion de ubicacion SOS",
            locationUnavailable = "📍 Ubicacion no disponible",
            approximateLocation = { "Ubicacion aproximada: $it" },
        )
        val English = SosPreviewCatalog(
            locationUpdate = "SOS location update",
            locationUnavailable = "📍 Location unavailable",
            approximateLocation = { "Location (approximate): $it" },
        )
        val French = SosPreviewCatalog(
            locationUpdate = "Mise a jour de position SOS",
            locationUnavailable = "📍 Position indisponible",
            approximateLocation = { "Position approximative : $it" },
        )

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
        !message.hasLocation -> catalog.locationUnavailable
        else -> catalog.approximateLocation(message.mapsUrl.orEmpty())
    }
}
