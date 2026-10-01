@file:OptIn(
    kotlin.js.ExperimentalWasmJsInterop::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class,
)

package com.quata.web

import com.quata.core.platform.PreferenceStore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WebPostgrestRpcSessionRecoveryTest {
    private val configuration = WebRuntimeConfiguration(
        supabaseUrl = "https://project.supabase.co",
        supabasePublishableKey = "synthetic-key",
        wordpressBaseUrl = "https://egquata.com/",
    )

    @Test
    fun unauthorizedRpcRefreshesAndRetriesExactlyOnce() = runTest {
        val preferences = MemoryPreferences()
        val auth = WebAuthRepository(configuration, preferences)
        auth.restoreLocalSession()
        installRpcSessionRecoveryFetch(secondRpcStatus = 200, refreshStatus = 200)
        try {
            val result = WebPostgrestRpcClient(configuration, auth).post("quata_chat_get_thread", "{}")
            assertEquals("accepted", assertIs<WebPostgrestResult.Success>(result).body)
            assertEquals(2, rpcRequestCount())
            assertEquals(1, authRefreshRequestCount())
            assertEquals("Bearer old-access", rpcAuthorizationAt(0))
            assertEquals("Bearer new-access", rpcAuthorizationAt(1))
        } finally {
            restoreRpcSessionRecoveryFetch()
        }
    }

    @Test
    fun retryIsBoundedAndTerminalRefreshRejectionDoesNotReplay() = runTest {
        for ((secondRpcStatus, refreshStatus, expectedRpcCalls) in listOf(
            Triple(401, 200, 2),
            Triple(200, 401, 1),
        )) {
            val preferences = MemoryPreferences()
            val auth = WebAuthRepository(configuration, preferences)
            auth.restoreLocalSession()
            installRpcSessionRecoveryFetch(secondRpcStatus, refreshStatus)
            try {
                val result = WebPostgrestRpcClient(configuration, auth).post("quata_chat_get_thread", "{}")
                assertEquals(WebPostgrestFailureKind.Unauthorized, assertIs<WebPostgrestResult.Failure>(result).kind)
                assertEquals(expectedRpcCalls, rpcRequestCount())
                assertEquals(1, authRefreshRequestCount())
                if (refreshStatus == 401) {
                    assertEquals(null, preferences.getString(WebAuthStorage.AccessToken))
                }
            } finally {
                restoreRpcSessionRecoveryFetch()
            }
        }
    }

    @Test
    fun lateSuccessfulRefreshCannotOverwriteLoginOrReplayTheOldActor() = runTest {
        val preferences = MemoryPreferences()
        val auth = WebAuthRepository(configuration, preferences)
        auth.restoreLocalSession()
        installRpcSessionRecoveryFetch(secondRpcStatus = 200, refreshStatus = 200, gateRefresh = true)
        try {
            val usedTokens = mutableListOf<String>()
            val client = WebPostgrestRpcClient(configuration, auth) { _, _, accessToken, _ ->
                usedTokens += accessToken
                WebPostgrestResult.Failure(
                    WebPostgrestFailureKind.Unauthorized,
                    "postgrest_rpc_http_401",
                    401,
                )
            }
            val oldActorRequest = async {
                client.post("quata_chat_get_thread", "{}")
            }
            runCurrent()
            assertEquals(1, authRefreshRequestCount())

            assertEquals("member-8", auth.login("240", "000000000", "synthetic").getOrThrow().userId)
            releaseRpcSessionRefresh()

            val result = assertIs<WebPostgrestResult.Failure>(oldActorRequest.await())
            assertEquals(WebPostgrestFailureKind.Session, result.kind)
            assertEquals("web_session_changed", result.reason)
            assertEquals(listOf("old-access"), usedTokens)
            assertEquals("member-8", auth.activeProfileSessionOrNull()?.userId)
            assertEquals("new-login-access", preferences.getString(WebAuthStorage.AccessToken))
            assertEquals("new-login-refresh", preferences.getString(WebAuthStorage.RefreshToken))
        } finally {
            restoreRpcSessionRecoveryFetch()
        }
    }

    private class MemoryPreferences : PreferenceStore {
        private val values = mutableMapOf(
            WebAuthStorage.AccessToken to "old-access",
            WebAuthStorage.RefreshToken to "old-refresh",
            WebAuthStorage.WebSessionToken to "old-web",
            WebAuthStorage.UserId to "member-7",
            WebAuthStorage.ExpiresAt to "4102444800",
        )
        override suspend fun getString(key: String): String? = values[key]
        override suspend fun putString(key: String, value: String) { values[key] = value }
        override suspend fun remove(key: String) { values.remove(key) }
    }
}

private fun installRpcSessionRecoveryFetch(
    secondRpcStatus: Int,
    refreshStatus: Int,
    gateRefresh: Boolean = false,
): Unit = js("""
    (() => {
      if (globalThis.__quataRpcRecoveryTest) throw new Error('test_already_installed');
      const state = { original: globalThis.fetch, rpc: [], refresh: 0, refreshReleases: [] };
      globalThis.__quataRpcRecoveryTest = state;
      const response = (status, body) => ({
        ok: status >= 200 && status < 300,
        status,
        text: () => Promise.resolve(body),
        headers: { get: () => null },
      });
      globalThis.fetch = (url, options = {}) => {
        const value = String(url);
        if (value.includes('/rest/v1/rpc/')) {
          state.rpc.push(options.headers?.Authorization ?? null);
          const status = state.rpc.length === 1 ? 401 : secondRpcStatus;
          return Promise.resolve(response(status, status === 200 ? 'accepted' : 'unauthorized'));
        }
        if (value.includes('/auth/v1/token')) {
          state.refresh++;
          const body = refreshStatus === 200
            ? JSON.stringify({ access_token: 'new-access', refresh_token: 'new-refresh', expires_at: 4102444800 })
            : JSON.stringify({ error_code: 'refresh_token_not_found' });
          if (gateRefresh) {
            return new Promise(resolve => state.refreshReleases.push(() => resolve(response(refreshStatus, body))));
          }
          return Promise.resolve(response(refreshStatus, body));
        }
        if (value.includes('/functions/v1/quata-auth-bridge')) {
          return Promise.resolve(response(200, JSON.stringify({
            session: { access_token: 'new-login-access', refresh_token: 'new-login-refresh', expires_at: 4102444800 },
            profile: { id: 'member-8', is_official: true, display_name: 'Replacement' },
            web_session: { token: 'new-login-web' }
          })));
        }
        return Promise.reject(new Error('unexpected_test_request'));
      };
    })()
""")

private fun rpcRequestCount(): Int = js("globalThis.__quataRpcRecoveryTest.rpc.length")
private fun authRefreshRequestCount(): Int = js("globalThis.__quataRpcRecoveryTest.refresh")
private fun rpcAuthorizationAt(index: Int): String? = js("globalThis.__quataRpcRecoveryTest.rpc[index]")
private fun releaseRpcSessionRefresh(): Unit = js("globalThis.__quataRpcRecoveryTest.refreshReleases.splice(0).forEach(release => release())")
private fun restoreRpcSessionRecoveryFetch(): Unit = js("(() => { const state = globalThis.__quataRpcRecoveryTest; globalThis.fetch = state.original; delete globalThis.__quataRpcRecoveryTest; })()")
