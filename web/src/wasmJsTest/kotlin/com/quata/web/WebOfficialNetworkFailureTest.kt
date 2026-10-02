@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.platform.PreferenceStore
import com.quata.feature.official.domain.OfficialFeedCursor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebOfficialNetworkFailureTest {
    @Test
    fun actualRepositoryReturnsNetworkFailureForTheExactOlderPageRequest() = runTest {
        installOfficialFetchNetworkFailure()
        try {
            val configuration = WebRuntimeConfiguration(
                supabaseUrl = "https://project.supabase.co",
                supabasePublishableKey = "public-client-key",
            )
            val auth = WebAuthRepository(configuration, EmptyPreferences())
            val repository = WebOfficialRepository(WebPostgrestClient(configuration, auth), auth)
            val cursor = OfficialFeedCursor(
                sortAt = "2026-10-02T09:00:00Z",
                createdAt = "2026-10-02T08:00:00Z",
                postId = "00000000-0000-4000-8000-000000000105",
            )

            val failure = assertIs<WebPostgrestReadException>(
                repository.loadOlderOfficialFeedPage(cursor, 25).exceptionOrNull(),
            )

            assertEquals(WebPostgrestFailureKind.Network, failure.failure.kind)
            assertEquals("synthetic_official_network_failure", failure.failure.reason)
            assertEquals(1, officialFetchRequestCount())
            val requestUrl = officialFetchLastUrl()
            assertTrue(requestUrl.contains("/rest/v1/rpc/quata_official_feed_page?"))
            assertTrue(requestUrl.contains("p_limit=25"))
            assertTrue(requestUrl.contains("p_before_sort_at=2026-10-02T09%3A00%3A00Z"))
            assertTrue(requestUrl.contains("p_before_created_at=2026-10-02T08%3A00%3A00Z"))
            assertTrue(requestUrl.contains("p_before_id=00000000-0000-4000-8000-000000000105"))
            assertEquals(false, officialFetchSentAuthorization())
        } finally {
            restoreOfficialFetch()
        }
    }

    private class EmptyPreferences : PreferenceStore {
        override suspend fun getString(key: String): String? = null
        override suspend fun putString(key: String, value: String) = Unit
        override suspend fun remove(key: String) = Unit
    }
}

private fun installOfficialFetchNetworkFailure(): Unit = js(
    """
    (() => {
      if (globalThis.__quataOfficialNetworkTest) throw new Error('test_already_installed');
      const state = { original: globalThis.fetch, requests: 0, lastUrl: '', authorization: false };
      globalThis.__quataOfficialNetworkTest = state;
      globalThis.fetch = (url, options = {}) => {
        state.requests++;
        state.lastUrl = String(url);
        state.authorization = Boolean(options?.headers?.Authorization);
        return Promise.reject(new Error('synthetic_official_network_failure'));
      };
    })()
    """,
)

private fun officialFetchRequestCount(): Int = js("globalThis.__quataOfficialNetworkTest.requests")
private fun officialFetchLastUrl(): String = js("globalThis.__quataOfficialNetworkTest.lastUrl")
private fun officialFetchSentAuthorization(): Boolean = js("globalThis.__quataOfficialNetworkTest.authorization")
private fun restoreOfficialFetch(): Unit = js("(() => { const state = globalThis.__quataOfficialNetworkTest; globalThis.fetch = state.original; delete globalThis.__quataOfficialNetworkTest; })()")
