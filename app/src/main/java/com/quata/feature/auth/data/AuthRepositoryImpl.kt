package com.quata.feature.auth.data

import android.content.Context
import com.quata.core.auth.GoogleAuthHelper
import com.quata.core.config.AppConfig
import com.quata.core.data.MockData
import com.quata.core.model.AuthSession
import com.quata.core.session.SessionManager
import com.quata.feature.auth.domain.AuthRepository

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val sessionManager: SessionManager,
    private val googleAuthHelper: GoogleAuthHelper
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<AuthSession> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            AuthSession(
                token = "mock-wp-token",
                userId = MockData.currentUser.id,
                email = email,
                displayName = MockData.currentUser.displayName
            )
        } else {
            val response = remoteDataSource.login(email, password)
            AuthSession(
                token = response.token ?: error("WordPress no devolvió token"),
                userId = response.userNiceName ?: response.userEmail ?: email,
                email = response.userEmail ?: email,
                displayName = response.userDisplayName ?: response.userNiceName ?: email
            )
        }
    }.onSuccess { sessionManager.setSession(it) }

    override suspend fun register(email: String, password: String, displayName: String): Result<AuthSession> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) {
            AuthSession("mock-register-token", "mock-user-id", email, displayName)
        } else {
            val response = remoteDataSource.register(email, password, displayName)
            AuthSession(
                token = response.token ?: "",
                userId = response.id ?: response.email ?: email,
                email = response.email ?: email,
                displayName = response.displayName ?: displayName
            )
        }
    }.onSuccess { sessionManager.setSession(it) }

    override suspend fun loginWithGoogle(context: Context): Result<AuthSession> {
        return googleAuthHelper.signIn(context).onSuccess { sessionManager.setSession(it) }
    }

    override suspend fun logout() {
        sessionManager.clearSession()
    }
}
