package com.quata.core.platform

import platform.Foundation.NSUserDefaults

/** iOS-backed [PreferenceStore] using the app's standard user defaults suite. */
class IosPreferenceStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : PreferenceStore {
    override suspend fun getString(key: String): String? = defaults.stringForKey(key)

    override suspend fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }

    override suspend fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
