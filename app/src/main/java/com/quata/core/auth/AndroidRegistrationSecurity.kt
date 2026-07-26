package com.quata.core.auth

import android.content.Context
import java.util.UUID

data class RegistrationChallenge(val token: String)

class AndroidRegistrationChallengeService {
    @Volatile private var host: (suspend () -> RegistrationChallenge)? = null

    fun attachHost(request: suspend () -> RegistrationChallenge) { host = request }
    fun detachHost() { host = null }

    suspend fun acquire(): RegistrationChallenge =
        host?.invoke() ?: error("registration_challenge_host_unavailable")
}

class RegistrationClientIdentityStore(context: Context) {
    private val preferences = context.getSharedPreferences("registration_security", Context.MODE_PRIVATE)

    fun clientInstanceId(): String = preferences.getString(ClientInstanceIdKey, null)
        ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(ClientInstanceIdKey, it).apply()
        }

    fun idempotencyKey(identity: String): String {
        val key = "pending_${identity.filter(Char::isDigit)}"
        return preferences.getString(key, null)
            ?: UUID.randomUUID().toString().also { preferences.edit().putString(key, it).apply() }
    }

    fun complete(identity: String) {
        preferences.edit().remove("pending_${identity.filter(Char::isDigit)}").apply()
    }

    private companion object {
        const val ClientInstanceIdKey = "client_instance_id"
    }
}

const val TurnstileRegisterAction = "register_android"
