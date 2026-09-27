package com.quata.data.supabase

internal class CachedResponseEmissionGate {
    private var lastBody: String? = null
    private var forceNextEmission = false

    var hasValue: Boolean = false
        private set

    fun markInvalidated(emitUnchangedAfterInvalidation: Boolean) {
        if (hasValue) forceNextEmission = emitUnchangedAfterInvalidation
    }

    fun accepts(body: String): Boolean {
        if (!forceNextEmission && body == lastBody) return false
        forceNextEmission = false
        lastBody = body
        hasValue = true
        return true
    }
}
