@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.quata.web

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebAuthRefreshRejectionTest {
    private val configuration = WebRuntimeConfiguration(
        supabaseUrl = "https://project.supabase.co",
        supabasePublishableKey = "synthetic-key",
        wordpressBaseUrl = "https://egquata.com/",
    )

    @Test
    fun concurrentCallersAndLaterRequestDoNotRetryTerminalRejection() = runTest {
        val preferences = MemoryPreferences()
        val repository = WebAuthRepository(configuration, preferences)
        preferences.putString(WebAuthStorage.ExpiresAt, "4102444800")
        repository.restoreLocalSession()
        preferences.putString(WebAuthStorage.ExpiresAt, "0")
        installRefreshResponse(400, """{"error_code":"refresh_token_not_found"}""", true, false)
        try {
            val first = async { repository.sessionForAuthenticatedRequest() }
            val second = async { repository.sessionForAuthenticatedRequest() }
            runCurrent()
            assertEquals(1, refreshRequestCount())
            releaseRefreshResponse()
            assertNull(first.await())
            assertNull(second.await())
            assertNull(repository.sessionForAuthenticatedRequest())
            assertEquals(1, refreshRequestCount())
            assertNull(preferences.getString(WebAuthStorage.AccessToken))
            assertNull(preferences.getString(WebAuthStorage.RefreshToken))
            assertNull(preferences.getString(WebSessionReadyKey))
            assertNull(repository.activeProfileSessionOrNull())
        } finally { restoreRefreshFetch() }
    }

    @Test
    fun bothTerminalAuthCodesClearOnlyOnBadRequestOrUnauthorized() = runTest {
        for (status in listOf(400, 401)) for (code in listOf("refresh_token_not_found", "session_not_found")) {
            val preferences = MemoryPreferences()
            installRefreshResponse(status, """{"error_code":"$code"}""", false, false)
            try {
                assertNull(WebAuthRepository(configuration, preferences).sessionForAuthenticatedRequest())
                assertNull(preferences.getString(WebAuthStorage.AccessToken))
                assertEquals(1, refreshRequestCount())
            } finally { restoreRefreshFetch() }
        }
    }

    @Test
    fun transientUnknownAndMalformedFailuresPreserveCredentials() = runTest {
        val responses = listOf(
            0 to "network",
            400 to """{"error_code":"unexpected_failure"}""",
            401 to """{"error":"session_not_found"}""",
            429 to """{"error_code":"refresh_token_not_found"}""",
            500 to """{"error_code":"session_not_found"}""",
            400 to "not-json",
        )
        for ((status, body) in responses) {
            val preferences = MemoryPreferences()
            val repository = WebAuthRepository(configuration, preferences)
            installRefreshResponse(status, body, false, false)
            try {
                assertNull(repository.sessionForAuthenticatedRequest())
                assertEquals("old-access", preferences.getString(WebAuthStorage.AccessToken))
                assertEquals("old-refresh", preferences.getString(WebAuthStorage.RefreshToken))
                assertNull(repository.sessionForAuthenticatedRequest())
                assertEquals(2, refreshRequestCount())
            } finally { restoreRefreshFetch() }
        }
    }

    @Test
    fun delayedRejectionCannotEraseNewLoginCommittedWhileRefreshWaits() = runTest {
        val preferences = MemoryPreferences()
        val repository = WebAuthRepository(configuration, preferences)
        installRefreshResponse(400, """{"error_code":"session_not_found"}""", true, false)
        try {
            val oldRequest = async { repository.sessionForAuthenticatedRequest() }
            runCurrent()
            assertEquals(1, refreshRequestCount())
            assertTrue(repository.login("240", "000000000", "synthetic").isSuccess)
            assertEquals("new-access", repository.activeProfileSessionOrNull()?.accessToken)
            releaseRefreshResponse()
            assertNull(oldRequest.await())
            assertEquals("new-access", preferences.getString(WebAuthStorage.AccessToken))
            assertEquals("new-refresh", preferences.getString(WebAuthStorage.RefreshToken))
            assertEquals("new-profile", repository.activeProfileSessionOrNull()?.userId)
            assertEquals("new-access", repository.sessionForAuthenticatedRequest()?.accessToken)
            assertEquals(1, refreshRequestCount())
        } finally { restoreRefreshFetch() }
    }

    @Test
    fun sameCodeFromLoginIsNotClassifiedAsTerminalRefresh() = runTest {
        val preferences = MemoryPreferences()
        installRefreshResponse(401, """{"error_code":"session_not_found"}""", false, true)
        try {
            val result = WebAuthRepository(configuration, preferences).login("240", "000000000", "synthetic")
            assertFalse(result.isSuccess)
            assertEquals("web_auth_http_401", result.exceptionOrNull()?.message)
            assertEquals("old-access", preferences.getString(WebAuthStorage.AccessToken))
        } finally { restoreRefreshFetch() }
    }

    private class MemoryPreferences : PreferenceStore {
        private val values = mutableMapOf(
            WebAuthStorage.AccessToken to "old-access", WebAuthStorage.RefreshToken to "old-refresh",
            WebAuthStorage.WebSessionToken to "old-web", WebAuthStorage.UserId to "old-profile",
            WebAuthStorage.ExpiresAt to "0", WebSessionReadyKey to "true",
        )
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}

private fun installRefreshResponse(status: Int, body: String, gated: Boolean, loginFails: Boolean): Unit = js("""
    (() => {
      if (globalThis.__quataRefreshTest) throw new Error('test_already_installed');
      const state = { original: globalThis.fetch, requests: 0, releases: [] };
      globalThis.__quataRefreshTest = state;
      const response = () => ({ ok: status >= 200 && status < 300, status, text: () => Promise.resolve(body) });
      globalThis.fetch = (url) => {
        if (String(url).includes('/auth/v1/token')) {
          state.requests++;
          if (status === 0) return Promise.reject(new Error('synthetic_network_failure'));
          if (gated) return new Promise(resolve => state.releases.push(() => resolve(response())));
          return Promise.resolve(response());
        }
        if (String(url).includes('/functions/v1/quata-auth-bridge')) {
          if (loginFails) return Promise.resolve(response());
          return Promise.resolve({ ok: true, status: 200, text: () => Promise.resolve(JSON.stringify({
            session: { access_token: 'new-access', refresh_token: 'new-refresh', expires_at: 4102444800 },
            profile: { id: 'new-profile', is_official: true, display_name: 'Synthetic' },
            web_session: { token: 'new-web' }
          })) });
        }
        return Promise.reject(new Error('unexpected_test_request'));
      };
    })()
""")
private fun refreshRequestCount(): Int = js("globalThis.__quataRefreshTest.requests")
private fun releaseRefreshResponse(): Unit = js("globalThis.__quataRefreshTest.releases.splice(0).forEach(release => release())")
private fun restoreRefreshFetch(): Unit = js("(() => { const state = globalThis.__quataRefreshTest; globalThis.fetch = state.original; delete globalThis.__quataRefreshTest; })()")
