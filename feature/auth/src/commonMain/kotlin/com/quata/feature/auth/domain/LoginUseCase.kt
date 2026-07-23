package com.quata.feature.auth.domain

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(countryCode: String, phone: String, password: String) =
        repository.login(countryCode, phone, password)
}
