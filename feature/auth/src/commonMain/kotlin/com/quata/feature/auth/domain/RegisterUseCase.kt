package com.quata.feature.auth.domain

class RegisterUseCase(private val repository: RegisterRepository) {
    suspend operator fun invoke(request: RegisterAccountRequest) = repository.register(request)
}
