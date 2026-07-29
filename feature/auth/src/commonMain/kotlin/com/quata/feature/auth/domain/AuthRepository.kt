package com.quata.feature.auth.domain

import com.quata.core.model.AuthSession

interface LoginRepository {
    suspend fun login(countryCode: String, phone: String, password: String): Result<AuthSession>
}

interface AuthRepository : LoginRepository {
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
    /** Android may know the profile id; the public recovery bridge intentionally returns only the question. */
    val userId: String? = null,
    val secretQuestion: String
)
