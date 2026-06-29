package com.quata.core.text

import android.content.Context
import com.quata.R
import java.util.Locale

data class LocalizedSosMessage(
    val title: String,
    val body: String?,
    val locationLabel: String?,
    val mapsUrl: String?,
    val age: String?,
    val accuracy: String?,
    val speed: String?,
    val isUpdate: Boolean,
    val isUnavailable: Boolean
)

fun Context.localizedSosMessage(raw: String): LocalizedSosMessage? {
    val parsed = raw.parseSosShortcode() ?: return null
    val senderName = parsed.senderName.ifBlank { getString(R.string.user_fallback_name) }
    val title = when (parsed.kind) {
        SosShortcodeKind.Alert -> parsed.customMessage ?: getString(R.string.sos_default_message, senderName)
        SosShortcodeKind.LocationUpdate -> getString(R.string.sos_location_update)
    }
    val body = when (parsed.kind) {
        SosShortcodeKind.Alert -> null
        SosShortcodeKind.LocationUpdate -> getString(R.string.sos_location_updated_body)
    }
    val locationLabel = parsed.mapsUrl?.let { url ->
        if (parsed.kind == SosShortcodeKind.LocationUpdate) {
            getString(R.string.sos_location, url)
        } else {
            getString(R.string.sos_location_approx, url)
        }
    }
    return LocalizedSosMessage(
        title = title,
        body = body,
        locationLabel = locationLabel,
        mapsUrl = parsed.mapsUrl,
        age = parsed.ageMillis?.formatLocalizedSosAge(this),
        accuracy = parsed.accuracyMeters?.let { getString(R.string.sos_distance_meters, it.roundedSosNumber()) },
        speed = parsed.speedKmh?.let { getString(R.string.sos_speed_kmh, it.oneDecimalSosNumber()) },
        isUpdate = parsed.kind == SosShortcodeKind.LocationUpdate,
        isUnavailable = !parsed.hasLocation
    )
}

fun Context.localizedSosPreview(raw: String): String? {
    val message = localizedSosMessage(raw) ?: return null
    return when {
        message.isUpdate -> getString(R.string.sos_location_update)
        message.isUnavailable -> getString(R.string.sos_location_unavailable)
        else -> message.locationLabel ?: message.title
    }
}

private fun Long.formatLocalizedSosAge(context: Context): String {
    val totalMinutes = coerceAtLeast(0L) / 60_000L
    if (totalMinutes < 1L) return context.getString(R.string.sos_age_less_than_minute)
    if (totalMinutes < 60L) return context.resources.getQuantityString(R.plurals.sos_age_minutes, totalMinutes.toInt(), totalMinutes)
    val totalHours = totalMinutes / 60L
    if (totalHours < 24L) return context.resources.getQuantityString(R.plurals.sos_age_hours, totalHours.toInt(), totalHours)
    val totalDays = totalHours / 24L
    return context.resources.getQuantityString(R.plurals.sos_age_days, totalDays.toInt(), totalDays)
}

private fun Double.roundedSosNumber(): String =
    String.format(Locale.ROOT, "%.0f", this)

private fun Double.oneDecimalSosNumber(): String =
    String.format(Locale.ROOT, "%.1f", this)
