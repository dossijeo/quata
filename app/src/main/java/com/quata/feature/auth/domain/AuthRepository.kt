package com.quata.feature.auth.domain

import android.content.Context
import com.quata.core.model.AuthSession

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<AuthSession>
    suspend fun register(email: String, password: String, displayName: String): Result<AuthSession>
    suspend fun loginWithGoogle(context: Context): Result<AuthSession>
    suspend fun logout()
}
