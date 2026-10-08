@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserPreferenceStoreTest {
    private val key = "quata.test.atomic-preference"

    @BeforeTest
    fun installDeterministicBrowserStorageAndLocks() {
        browserPreferenceTestInstallEnvironment()
    }

    @AfterTest
    fun cleanup() {
        browserPreferenceTestReleaseLock(key)
        browserPreferenceTestRemove(key)
        browserPreferenceTestRestoreEnvironment()
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

private fun browserPreferenceTestInstallEnvironment(): Unit = js("""(() => {
    if (typeof globalThis.navigator?.locks?.request === 'function' && !!globalThis.localStorage) {
      globalThis.__quataPreferenceTestEnvironment = { simulated: false };
      return;
    }
    const isNodeRunner = typeof globalThis.window === 'undefined' &&
      typeof globalThis.document === 'undefined';
    if (!isNodeRunner) {
      globalThis.__quataPreferenceTestEnvironment = { simulated: false };
      return;
    }
    const storageDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
    const navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
    const originalNavigator = globalThis.navigator;
    const locksDescriptor = originalNavigator
      ? Object.getOwnPropertyDescriptor(originalNavigator, 'locks')
      : undefined;
    const values = new Map();
    const queues = new Map();
    const storage = {
      get length() { return values.size; },
      key(index) { return Array.from(values.keys())[index] ?? null; },
      getItem(key) { return values.has(String(key)) ? values.get(String(key)) : null; },
      setItem(key, value) { values.set(String(key), String(value)); },
      removeItem(key) { values.delete(String(key)); },
      clear() { values.clear(); },
    };
    const locks = {
      request(name, _options, callback) {
        const previous = queues.get(name) ?? Promise.resolve();
        const current = previous.catch(() => undefined).then(() => callback());
        const settled = current.catch(() => undefined);
        queues.set(name, settled);
        settled.finally(() => {
          if (queues.get(name) === settled) queues.delete(name);
        });
        return current;
      },
    };
    const navigator = originalNavigator ?? {};
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      enumerable: true,
      writable: true,
      value: storage,
    });
    if (!originalNavigator) {
      Object.defineProperty(globalThis, 'navigator', {
        configurable: true,
        enumerable: true,
        writable: true,
        value: navigator,
      });
    }
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      enumerable: true,
      writable: true,
      value: locks,
    });
    globalThis.__quataPreferenceTestEnvironment = {
      simulated: true,
      storageDescriptor,
      navigatorDescriptor,
      originalNavigator,
      locksDescriptor,
    };
})()""")

private fun browserPreferenceTestRestoreEnvironment(): Unit = js("""(() => {
    const environment = globalThis.__quataPreferenceTestEnvironment;
    delete globalThis.__quataPreferenceTestEnvironment;
    delete globalThis.__quataPreferenceTestLocks;
    if (!environment?.simulated) return;
    if (environment.storageDescriptor) {
      Object.defineProperty(globalThis, 'localStorage', environment.storageDescriptor);
    } else {
      delete globalThis.localStorage;
    }
    if (environment.originalNavigator) {
      if (environment.locksDescriptor) {
        Object.defineProperty(environment.originalNavigator, 'locks', environment.locksDescriptor);
      } else {
        delete environment.originalNavigator.locks;
      }
    } else if (environment.navigatorDescriptor) {
      Object.defineProperty(globalThis, 'navigator', environment.navigatorDescriptor);
    } else {
      delete globalThis.navigator;
    }
})()""")

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
