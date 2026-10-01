@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.core.platform

/** Browser-backed [PreferenceStore] for WebAssembly hosts. */
class BrowserPreferenceStore : PrefixClearablePreferenceStore {
    override suspend fun getString(key: String): String? = browserPreferenceGet(key)

    override suspend fun putString(key: String, value: String) {
        browserPreferencePut(key, value)
    }

    override suspend fun remove(key: String) {
        browserPreferenceRemove(key)
    }

    override suspend fun removeByPrefix(prefix: String) {
        browserPreferenceRemoveByPrefix(prefix)
    }
}

private fun browserPreferenceGet(key: String): String? =
    js("globalThis.localStorage?.getItem(key) ?? null")

private fun browserPreferencePut(key: String, value: String): Unit =
    js("globalThis.localStorage?.setItem(key, value)")

private fun browserPreferenceRemove(key: String): Unit =
    js("globalThis.localStorage?.removeItem(key)")

private fun browserPreferenceRemoveByPrefix(prefix: String): Unit = js("""(() => {
    const storage = globalThis.localStorage;
    if (!storage) return;
    const keys = [];
    for (let index = 0; index < storage.length; index += 1) {
        const key = storage.key(index);
        if (key?.startsWith(prefix)) keys.push(key);
    }
    keys.forEach((key) => storage.removeItem(key));
})()""")
