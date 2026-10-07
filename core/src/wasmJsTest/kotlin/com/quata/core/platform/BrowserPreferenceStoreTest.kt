@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserPreferenceStoreTest {
    private val key = "quata.test.atomic-preference"

    @AfterTest
    fun cleanup() {
        browserPreferenceTestRemove(key)
    }

    @Test
    fun separateBrowserStoresSerializeConcurrentUpdates() = runTest {
        if (!browserPreferenceLocksAvailable()) return@runTest
        val first = BrowserPreferenceStore()
        val second = BrowserPreferenceStore()

        val updated = listOf(first to "first", second to "second").map { (store, value) ->
            async {
                store.updateStringAtomically(key) { current ->
                    listOfNotNull(current, value).joinToString(",")
                }
            }
        }.awaitAll()

        assertTrue(updated.all { it })
        assertEquals(setOf("first", "second"), first.getString(key)?.split(',')?.toSet())
    }
}

private fun browserPreferenceLocksAvailable(): Boolean =
    js("typeof globalThis.navigator?.locks?.request === 'function' && !!globalThis.localStorage")

private fun browserPreferenceTestRemove(key: String): Unit =
    js("globalThis.localStorage?.removeItem(key)")
