package com.quata.core.preferences

import android.content.Context
import com.quata.core.designsystem.theme.QuataThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("quata_theme", Context.MODE_PRIVATE)
    private val preferenceChanges = MutableStateFlow(0)

    fun observeThemeMode(): Flow<QuataThemeMode> =
        preferenceChanges
            .map { themeMode() }
            .onStart { emit(themeMode()) }
            .distinctUntilChanged()

    fun themeMode(): QuataThemeMode =
        QuataThemeMode.fromStorageValue(prefs.getString(KEY_THEME_MODE, QuataThemeMode.System.storageValue))

    fun setThemeMode(mode: QuataThemeMode) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
        preferenceChanges.value += 1
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
