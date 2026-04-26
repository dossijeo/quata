package com.quata.feature.auth.domain

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, displayName: String) = repository.register(email, password, displayName)
}
