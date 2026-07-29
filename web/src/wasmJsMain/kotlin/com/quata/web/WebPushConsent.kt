package com.quata.web

import com.quata.core.platform.PreferenceStore

internal enum class WebPushConsentState { Unset, Enabled, Disabled }

/** Versioned local consent. Absence invokes the safe pre-v1 migration path without prompting. */
object WebPushConsent {
    const val PreferenceKey = "web.push.consent.v1"
    private const val Enabled = "enabled"

    internal suspend fun state(preferences: PreferenceStore): WebPushConsentState =
        when (preferences.getString(PreferenceKey)) {
            Enabled -> WebPushConsentState.Enabled
            "disabled" -> WebPushConsentState.Disabled
            else -> WebPushConsentState.Unset
        }

    suspend fun isEnabled(preferences: PreferenceStore): Boolean =
        state(preferences) == WebPushConsentState.Enabled

    suspend fun setEnabled(preferences: PreferenceStore, enabled: Boolean) {
        preferences.putString(PreferenceKey, if (enabled) Enabled else "disabled")
    }
}
