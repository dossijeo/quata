package com.quata.feature.profile.data

import android.content.Context

class EmergencyContactsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "quata_emergency_contacts",
        Context.MODE_PRIVATE
    )

    fun get(profileId: String): List<String> =
        preferences.getString(key(profileId), null)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(MaxContacts)
            .orEmpty()

    fun save(profileId: String, contactIds: List<String>) {
        preferences.edit()
            .putString(key(profileId), normalize(contactIds).joinToString(separator = "\n"))
            .apply()
    }

    private fun normalize(contactIds: List<String>): List<String> =
        contactIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MaxContacts)

    private fun key(profileId: String): String = "contacts_${profileId.trim()}"

    private companion object {
        const val MaxContacts = 5
    }
}
