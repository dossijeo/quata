package com.quata.core.auth

import android.content.Context
import com.quata.core.config.AppConfig
import com.quata.core.model.AuthSession

class GoogleAuthHelper {
    /**
     * Placeholder preparado para integrar Google Sign-In real.
     * Recomendación actual: Credential Manager + Google ID token, y luego intercambiarlo
     * por una sesión propia en WordPress o Supabase.
     */
    suspend fun signIn(context: Context): Result<AuthSession> {
        return if (AppConfig.USE_MOCK_BACKEND) {
            Result.success(
                AuthSession(
                    token = "mock-google-token",
                    userId = "google_user_mock",
                    email = "google-user@quata.app",
                    displayName = "Usuario Google"
                )
            )
        } else {
            Result.failure(IllegalStateException("Google Sign-In real pendiente: configura Credential Manager y GOOGLE_WEB_CLIENT_ID."))
        }
    }
}
