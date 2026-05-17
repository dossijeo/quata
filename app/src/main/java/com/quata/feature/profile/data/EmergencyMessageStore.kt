package com.quata.feature.profile.data

import android.content.Context

class EmergencyMessageStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun get(profileId: String): StoredEmergencyMessage? {
        val message = preferences.getString(messageKey(profileId), null) ?: return null
        val isDefault = preferences.getBoolean(defaultKey(profileId), true)
        return StoredEmergencyMessage(message = message, isDefault = isDefault)
    }

    fun save(profileId: String, message: String, isDefault: Boolean) {
        preferences.edit()
            .putString(messageKey(profileId), message)
            .putBoolean(defaultKey(profileId), isDefault)
            .apply()
    }

    private fun messageKey(profileId: String): String = "${profileId.safeKey()}:message"

    private fun defaultKey(profileId: String): String = "${profileId.safeKey()}:is_default"

    private fun String.safeKey(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

    private companion object {
        const val PREFERENCES_NAME = "quata_emergency_messages"
    }
}

data class StoredEmergencyMessage(
    val message: String,
    val isDefault: Boolean
)
