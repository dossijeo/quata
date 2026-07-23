package com.quata.core.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await

class AndroidClipboardService(context: Context) : ClipboardService {
    private val clipboard = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override suspend fun readText(): String? = clipboard.primaryClip?.getItemAt(0)?.coerceToText(null)?.toString()

    override suspend fun writeText(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("quata", text))
    }
}

class AndroidPreferenceStore(context: Context, name: String = "quata_platform") : PreferenceStore {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override suspend fun getString(key: String): String? = preferences.getString(key, null)

    override suspend fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override suspend fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}

class AndroidShareService(context: Context) : ShareService {
    private val applicationContext = context.applicationContext

    override suspend fun share(payload: SharePayload): PlatformResult<Unit> = runCatching {
        val files = payload.files.mapNotNull { file -> Uri.parse(file.reference).takeIf { it.scheme == "content" } }
        if (payload.files.isNotEmpty() && files.size != payload.files.size) return PlatformResult.Unsupported
        val intent = when {
            files.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files))
            }
            files.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = payload.files.first().mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, files.first())
            }
            else -> Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        }.apply {
            payload.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            payload.title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            if (files.isNotEmpty()) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        applicationContext.startActivity(Intent.createChooser(intent, payload.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        PlatformResult.Success(Unit)
    }.getOrElse { PlatformResult.Failure(it.message) }
}

class AndroidLocationService(context: Context) : LocationService {
    private val applicationContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(applicationContext)

    override suspend fun currentLocation(): PlatformResult<GeoLocation> {
        val hasFine = ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return PlatformResult.Failure("location_permission_denied")
        return runCatching {
            val location = client.getCurrentLocation(if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                ?: return PlatformResult.Failure("location_unavailable")
            PlatformResult.Success(GeoLocation(location.latitude, location.longitude, location.accuracy, location.time))
        }.getOrElse { PlatformResult.Failure(it.message) }
    }
}
