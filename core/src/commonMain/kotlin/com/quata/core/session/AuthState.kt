package com.quata.core.session

/** Platform-neutral session state. */

sealed class AuthState {
    data object LoggedOut : AuthState()
    data class LoggedIn(val userId: String, val displayName: String) : AuthState()
}
