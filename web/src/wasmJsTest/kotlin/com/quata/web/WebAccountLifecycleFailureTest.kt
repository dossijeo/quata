@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebAccountLifecycleFailureTest {
    private val configuration = WebRuntimeConfiguration(
        supabaseUrl = "https://project.supabase.co",
        supabasePublishableKey = "synthetic-key",
        wordpressBaseUrl = "https://egquata.com/",
    )

    @Test
    fun incorrectPasswordKeepsSessionAndExposesStableCode() = runTest {
        val preferences = MemoryPreferences()
        installLifecycleResponse(403, """{"error":"invalid_password"}""")
        try {
            val repository = WebAuthRepository(configuration, preferences)

            val result = repository.deactivateAccount("incorrect-password")

            assertTrue(result.isFailure)
            assertEquals("web_auth_invalid_password", result.exceptionOrNull()?.message)
            assertNotNull(repository.restoreLocalSession())
            assertEquals("old-access", preferences.getString(WebAuthStorage.AccessToken))
        } finally {
            restoreLifecycleFetch()
        }
    }

    @Test
    fun transportFailureKeepsSessionForRetry() = runTest {
        val preferences = MemoryPreferences()
        installLifecycleNetworkFailure()
        try {
            val repository = WebAuthRepository(configuration, preferences)

            val result = repository.deleteAccountData("valid-but-offline-password")

            assertTrue(result.isFailure)
            assertEquals("synthetic_network_failure", result.exceptionOrNull()?.message)
            assertNotNull(repository.restoreLocalSession())
            assertEquals("old-refresh", preferences.getString(WebAuthStorage.RefreshToken))
        } finally {
            restoreLifecycleFetch()
        }
    }

    private class MemoryPreferences : PreferenceStore {
        private val values = mutableMapOf(
            WebAuthStorage.AccessToken to "old-access",
            WebAuthStorage.RefreshToken to "old-refresh",
            WebAuthStorage.WebSessionToken to "old-web",
            WebAuthStorage.UserId to "old-profile",
            WebAuthStorage.ExpiresAt to "4102444800",
            WebAuthStorage.DisplayName to "Synthetic",
            WebSessionReadyKey to "true",
        )

        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}

private fun installLifecycleResponse(status: Int, body: String): Unit = js("""
    (() => {
      if (globalThis.__quataLifecycleTest) throw new Error('test_already_installed');
      globalThis.__quataLifecycleTest = { original: globalThis.fetch };
      globalThis.fetch = (url) => {
        if (!String(url).includes('/functions/v1/quata-account-lifecycle')) {
          return Promise.reject(new Error('unexpected_test_request'));
        }
        return Promise.resolve({ ok: status >= 200 && status < 300, status, text: () => Promise.resolve(body) });
      };
    })()
""")

private fun installLifecycleNetworkFailure(): Unit = js("""
    (() => {
      if (globalThis.__quataLifecycleTest) throw new Error('test_already_installed');
      globalThis.__quataLifecycleTest = { original: globalThis.fetch };
      globalThis.fetch = (url) => {
        if (!String(url).includes('/functions/v1/quata-account-lifecycle')) {
          return Promise.reject(new Error('unexpected_test_request'));
        }
        return Promise.reject(new Error('synthetic_network_failure'));
      };
    })()
""")

private fun restoreLifecycleFetch(): Unit = js("""
    (() => {
      const state = globalThis.__quataLifecycleTest;
      globalThis.fetch = state.original;
      delete globalThis.__quataLifecycleTest;
    })()
""")
