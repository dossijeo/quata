package com.quata.core.moderation

import android.content.Context

/** Persists local UGC terms acceptance until it can be acknowledged by the server. */
class UgcTermsAcceptanceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isAccepted(userId: String, version: String): Boolean =
        preferences.getBoolean(acceptedKey(userId, version), false)

    fun isPending(userId: String, version: String): Boolean =
        preferences.getBoolean(pendingKey(userId, version), false)

    fun markAcceptedPendingSync(userId: String, version: String) {
        preferences.edit()
            .putBoolean(acceptedKey(userId, version), true)
            .putBoolean(pendingKey(userId, version), true)
            .apply()
    }

    fun markAcceptedSynced(userId: String, version: String) {
        preferences.edit()
            .putBoolean(acceptedKey(userId, version), true)
            .remove(pendingKey(userId, version))
            .apply()
    }

    fun clearUser(userId: String) {
        val suffix = ":$userId:"
        preferences.edit().apply {
            preferences.all.keys.filter { it.contains(suffix) }.forEach(::remove)
        }.apply()
    }

    private fun acceptedKey(userId: String, version: String): String = "accepted:$userId:$version"

    private fun pendingKey(userId: String, version: String): String = "pending:$userId:$version"

    private companion object {
        const val PREFERENCES_NAME = "quata_ugc_terms_acceptance"
    }
}
