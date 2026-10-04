package com.quata.feature.auth.domain

class GoogleLoginUseCase(private val provider: GoogleAuthProvider) {
    operator fun invoke(): suspend () -> Result<com.quata.core.model.AuthSession> = provider.beginSignIn()
}
