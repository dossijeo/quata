package com.quata.core.preferences

import com.quata.core.model.AuthSession

/** Persistent session boundary; each platform supplies its own storage adapter. */
interface SessionStorage {
    fun saveSession(session: AuthSession)
    fun getSession(): AuthSession?
    fun clear()
}
