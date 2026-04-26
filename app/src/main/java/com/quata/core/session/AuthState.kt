package com.quata.core.session

sealed class AuthState {
    data object LoggedOut : AuthState()
    data class LoggedIn(val userId: String, val displayName: String) : AuthState()
}
