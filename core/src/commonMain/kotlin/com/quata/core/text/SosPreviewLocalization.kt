package com.quata.core.text

/** Short, safe SOS text for notification surfaces; transport shortcodes stay hidden. */
data class SosPreviewCatalog(
    val locationUpdate: String,
    val locationUnavailable: String,
    val approximateLocation: (String) -> String,
) {
    companion object {
        val Spanish = SosPreviewCatalog(
            locationUpdate = "Actualizacion de ubicacion SOS",
            locationUnavailable = "📍 Ubicación no disponible",
            approximateLocation = { "Ubicacion aproximada: $it" },
        )
        val English = SosPreviewCatalog("SOS location update", "📍 Location unavailable") { "Location (approximate): $it" }
        val French = SosPreviewCatalog("Mise a jour de position SOS", "📍 Position indisponible") { "Position approximative : $it" }
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
