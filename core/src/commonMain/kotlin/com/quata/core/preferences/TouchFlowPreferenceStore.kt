package com.quata.core.preferences

import kotlinx.coroutines.flow.Flow

/** Persistent per-user setting for the shared Qüata Touch Flow component. */
interface TouchFlowPreferenceStore {
    fun observeEnabled(userId: String?): Flow<Boolean>
    fun isEnabled(userId: String?): Boolean
    fun setEnabled(userId: String?, enabled: Boolean)
    fun clear(userId: String)
}
