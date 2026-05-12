package com.quata.feature.auth.domain

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(request: RegisterAccountRequest) = repository.register(request)
}
