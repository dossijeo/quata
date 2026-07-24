package com.quata.core.platform

/** Browser-backed [PreferenceStore] for WebAssembly hosts. */
class BrowserPreferenceStore : PreferenceStore {
    override suspend fun getString(key: String): String? = browserPreferenceGet(key)

    override suspend fun putString(key: String, value: String) {
        browserPreferencePut(key, value)
    }

    override suspend fun remove(key: String) {
        browserPreferenceRemove(key)
    }
}

private fun browserPreferenceGet(key: String): String? =
    js("globalThis.localStorage?.getItem(key) ?? null")

private fun browserPreferencePut(key: String, value: String): Unit =
    js("globalThis.localStorage?.setItem(key, value)")

private fun browserPreferenceRemove(key: String): Unit =
    js("globalThis.localStorage?.removeItem(key)")
