@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
    fun updatesWaitForTheSharedBrowserLockAndPreserveBothValues() = runTest {
        assertTrue(browserPreferenceLocksAvailable(), "Web Locks are required by the browser gate")
        val first = BrowserPreferenceStore()
        val second = BrowserPreferenceStore()
        browserPreferenceTestHoldLock(key)

        val updates = listOf(first to "first", second to "second").map { (store, value) ->
            async {
                store.updateStringAtomically(key) { current ->
                    listOfNotNull(current, value).joinToString(",")
                }
            }
        }
        try {
            browserPreferenceTestWait()
            assertTrue(updates.none { it.isCompleted }, "updates must remain pending behind the held lock")
        } finally {
            browserPreferenceTestReleaseLock(key)
        }
        val updated = updates.awaitAll()

        assertTrue(updated.all { it })
        assertEquals(setOf("first", "second"), first.getString(key)?.split(',')?.toSet())
    }
}

private fun browserPreferenceLocksAvailable(): Boolean =
    js("typeof globalThis.navigator?.locks?.request === 'function' && !!globalThis.localStorage")

private suspend fun browserPreferenceTestHoldLock(key: String): Unit = suspendCoroutine { continuation ->
    browserPreferenceTestHoldLock(key) { continuation.resume(Unit) }
}

private fun browserPreferenceTestHoldLock(key: String, onHeld: () -> Unit): Unit = js("""(() => {
    globalThis.__quataPreferenceTestLocks ??= new Map();
    globalThis.navigator.locks.request('quata.preference.' + key, { mode: 'exclusive' }, () =>
      new Promise((resolve) => {
        globalThis.__quataPreferenceTestLocks.set(key, resolve);
        onHeld();
      })
    );
})()""")

private fun browserPreferenceTestReleaseLock(key: String): Unit = js("""(() => {
    const release = globalThis.__quataPreferenceTestLocks?.get(key);
    globalThis.__quataPreferenceTestLocks?.delete(key);
    if (release) release();
})()""")

private suspend fun browserPreferenceTestWait(): Unit = suspendCoroutine { continuation ->
    browserPreferenceTestWait { continuation.resume(Unit) }
}

private fun browserPreferenceTestWait(onComplete: () -> Unit): Unit =
    js("globalThis.setTimeout(onComplete, 25)")

private fun browserPreferenceTestRemove(key: String): Unit =
    js("globalThis.localStorage?.removeItem(key)")
