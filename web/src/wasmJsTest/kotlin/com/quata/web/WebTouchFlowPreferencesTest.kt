package com.quata.web

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebTouchFlowPreferencesTest {
    @Test
    fun preferenceKeysAreProfileScopedAndAnonymousAccessFailsClosed() = runTest {
        val preferences = MemoryPreferenceStore()

        assertNull(webTouchFlowEnabledKey(null))
        assertNull(webTouchFlowEnabledKey("  "))
        assertEquals("quata_web_touch_flow_enabled_profile-a", webTouchFlowEnabledKey(" profile-a "))
        assertFalse(restoreWebTouchFlowEnabled(preferences, null))
        assertFalse(restoreWebTouchFlowEnabled(preferences, "profile-a"))
    }

    @Test
    fun explicitLegacyPreferenceMigratesToOnlyTheValidatedProfile() = runTest {
        val preferences = MemoryPreferenceStore(
            mutableMapOf("quata_web_touch_flow_enabled" to "true"),
        )

        assertTrue(restoreWebTouchFlowEnabled(preferences, "profile-a"))
        assertEquals("true", preferences.values["quata_web_touch_flow_enabled_profile-a"])
        assertFalse("quata_web_touch_flow_enabled" in preferences.values)
        assertFalse(restoreWebTouchFlowEnabled(preferences, "profile-b"))
    }

    private class MemoryPreferenceStore(
        val values: MutableMap<String, String> = mutableMapOf(),
    ) : PreferenceStore {
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) {
            values[key] = value
        }
        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }
}
