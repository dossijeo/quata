@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebAuthLogoutScopeTest {
    private val configuration = WebRuntimeConfiguration(
        supabaseUrl = "https://project.supabase.co",
        supabasePublishableKey = "synthetic-key",
        wordpressBaseUrl = "https://egquata.com/",
    )

    @Test
    fun localAndGlobalLogoutUseExplicitScopesAndAlwaysClearTheBrowserSession() = runTest {
        suspend fun run(global: Boolean): List<String> {
            val preferences = SessionPreferences()
            installLogoutFetch()
            try {
                val browserUnsubscribeCalls = mutableListOf<String>()
                val result = WebAuthRepository(configuration, preferences).logoutWithBrowserUnsubscribe(
                    global = global,
                    browserUnsubscribe = {
                        browserUnsubscribeCalls += "browser"
                        Result.success(Unit)
                    },
                )

                assertTrue(result.isSuccess)
                assertEquals(listOf("browser"), browserUnsubscribeCalls)
                assertNull(preferences.getString(WebAuthStorage.AccessToken))
                assertNull(preferences.getString(WebAuthStorage.RefreshToken))
                return recordedLogoutUrls()
            } finally {
                restoreLogoutFetch()
            }
        }

        assertEquals(
            listOf(
                "https://project.supabase.co/functions/v1/quata-web-push",
                "https://project.supabase.co/auth/v1/logout?scope=local",
            ),
            run(global = false),
        )
        assertEquals(
            listOf(
                "https://project.supabase.co/functions/v1/quata-auth-global-logout",
            ),
            run(global = true),
        )
    }

    @Test
    fun failedGlobalLogoutKeepsBrowserCredentialsAndSkipsBrowserRetirementForRetry() = runTest {
        val preferences = SessionPreferences()
        installLogoutFetch(globalStatus = 503)
        try {
            var browserUnsubscribeCalls = 0
            val result = WebAuthRepository(configuration, preferences).logoutWithBrowserUnsubscribe(
                global = true,
                browserUnsubscribe = {
                    browserUnsubscribeCalls += 1
                    Result.success(Unit)
                },
            )

            assertTrue(result.isFailure)
            assertEquals(0, browserUnsubscribeCalls)
            assertEquals("access-token", preferences.getString(WebAuthStorage.AccessToken))
            assertEquals("refresh-token", preferences.getString(WebAuthStorage.RefreshToken))
        } finally {
            restoreLogoutFetch()
        }
    }

    private class SessionPreferences : PreferenceStore {
        private val values = mutableMapOf(
            WebAuthStorage.AccessToken to "access-token",
            WebAuthStorage.RefreshToken to "refresh-token",
            WebAuthStorage.WebSessionToken to "web-session-token",
            WebAuthStorage.UserId to "profile-id",
            WebAuthStorage.ExpiresAt to "4102444800",
            WebAuthStorage.DisplayName to "Synthetic",
            WebSessionReadyKey to "true",
        )

        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}

private fun installLogoutFetch(globalStatus: Int = 200): Unit = js(
    """
    (() => {
      if (globalThis.__quataLogoutScopeTest) throw new Error('test_already_installed');
      globalThis.__quataLogoutScopeTest = { original: globalThis.fetch, urls: [] };
      globalThis.fetch = (url) => {
        globalThis.__quataLogoutScopeTest.urls.push(String(url));
        const status = String(url).includes('/quata-auth-global-logout') ? globalStatus : 200;
        return Promise.resolve({ ok: status >= 200 && status < 300, status, text: () => Promise.resolve('{}') });
      };
    })()
    """,
)

private fun recordedLogoutUrls(): List<String> =
    logoutUrlsJson().removePrefix("[").removeSuffix("]")
        .split(',')
        .filter(String::isNotBlank)
        .map { it.trim().removeSurrounding("\"") }

private fun logoutUrlsJson(): String = js("JSON.stringify(globalThis.__quataLogoutScopeTest.urls)")

private fun restoreLogoutFetch(): Unit = js(
    """
    (() => {
      const state = globalThis.__quataLogoutScopeTest;
      globalThis.fetch = state.original;
      delete globalThis.__quataLogoutScopeTest;
    })()
    """,
)
