package com.quata.feature.auth.domain

import com.quata.core.model.AuthSession

interface AuthRepository {
    suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession>
    suspend fun register(request: RegisterAccountRequest): Result<AuthSession>
    suspend fun getPasswordRecoveryQuestion(countryCode: String, phone: String): Result<PasswordRecoveryQuestion?>
    suspend fun resetPassword(countryCode: String, phone: String, secretAnswer: String, newPassword: String): Result<Unit>
    suspend fun deactivateAccount(password: String): Result<Unit>
    suspend fun deleteAccountData(password: String): Result<Unit>
    suspend fun logout()
}

data class RegisterAccountRequest(
    val displayName: String,
    val neighborhood: String,
    val countryCode: String,
    val phone: String,
    val password: String,
    val secretQuestion: String,
    val secretAnswer: String
)

data class PasswordRecoveryQuestion(
    val userId: String,
    val secretQuestion: String
)
