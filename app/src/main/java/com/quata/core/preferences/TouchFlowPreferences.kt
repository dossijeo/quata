package com.quata.core.preferences

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class TouchFlowPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("quata_touch_flow", Context.MODE_PRIVATE)
    private val preferenceChanges = MutableStateFlow(0)

    fun observeEnabled(userId: String?): Flow<Boolean> =
        preferenceChanges
            .map { isEnabled(userId) }
            .onStart { emit(isEnabled(userId)) }
            .distinctUntilChanged()

    fun isEnabled(userId: String?): Boolean =
        userId
            ?.takeIf { it.isNotBlank() }
            ?.let { prefs.getBoolean(enabledKey(it), false) }
            ?: false

    fun setEnabled(userId: String?, enabled: Boolean) {
        val cleanUserId = userId?.takeIf { it.isNotBlank() } ?: return
        prefs.edit()
            .putBoolean(enabledKey(cleanUserId), enabled)
            .apply()
        preferenceChanges.value += 1
    }

    private fun enabledKey(userId: String): String = "touch_flow_enabled_$userId"
}
