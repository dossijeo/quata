package com.quata.core.preferences

import kotlinx.coroutines.flow.Flow

/** Platform-neutral persistence boundary for the selected visual theme. */
interface ThemeModeStorage {
    fun observeStoredThemeMode(): Flow<String>
    fun storedThemeMode(): String
    fun setStoredThemeMode(value: String)
}
