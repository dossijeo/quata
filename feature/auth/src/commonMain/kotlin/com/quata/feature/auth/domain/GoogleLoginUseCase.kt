package com.quata.feature.auth.domain

class GoogleLoginUseCase(private val provider: GoogleAuthProvider) {
    suspend operator fun invoke() = provider.signIn()
}
