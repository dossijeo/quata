package com.quata.feature.auth.domain

import android.content.Context

class GoogleLoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(context: Context) = repository.loginWithGoogle(context)
}
