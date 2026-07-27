package com.quata.data.supabase

/**
 * Compatibility boundary for Android's existing public auth Edge Function.
 *
 * The adapter intentionally delegates to [SupabaseCommunityApi] rather than issuing a
 * second request. This keeps the published Android request shape and the legacy Web
 * contract untouched while giving a single place to harden the client in later slices.
 */
internal class AuthBridgeBoundary(
    private val api: SupabaseCommunityApi,
    private val shadowVerifier: AuthBridgeShadowVerifier = AuthBridgeShadowVerifier.disabled()
) {
    suspend fun login(
        countryCode: String,
        phone: String,
        password: String,
        reactivateDeactivated: Boolean = false
    ): SupabaseAuthBridgeResponse = api.loginWithAuthBridge(
        countryCode = countryCode,
        phone = phone,
        password = password,
        reactivateDeactivated = reactivateDeactivated
    ).also(shadowVerifier::observeLogin)

    companion object {
        /** Existing Edge Function name. Do not version or replace it during the shadow phase. */
        const val ENDPOINT = "quata-auth-bridge"
    }
}

/**
 * Reports response-shape mismatches only when explicitly enabled. A mismatch is never
 * propagated to the caller, so enabling the flag cannot prevent a legacy login.
 */
internal class AuthBridgeShadowVerifier(
    private val enabled: Boolean,
    private val report: (AuthBridgeContractIssue) -> Unit = {}
) {
    fun observeLogin(response: SupabaseAuthBridgeResponse) {
        if (!enabled) return
        AuthBridgeResponseContract.loginIssues(response).forEach(report)
    }

    companion object {
        fun disabled(): AuthBridgeShadowVerifier = AuthBridgeShadowVerifier(enabled = false)
    }
}

/** Contract shared by the current Android adapter and the prepared Edge Function response. */
internal object AuthBridgeResponseContract {
    const val VERSION = 1

    fun loginIssues(response: SupabaseAuthBridgeResponse): Set<AuthBridgeContractIssue> = buildSet {
        if (response.profile.id.isBlank()) add(AuthBridgeContractIssue.MissingProfileId)
        if (response.user.id.isBlank()) add(AuthBridgeContractIssue.MissingAuthUserId)
        if (response.profile.auth_user_id?.isNotBlank() == true &&
            response.profile.auth_user_id != response.user.id
        ) {
            add(AuthBridgeContractIssue.AuthUserIdMismatch)
        }
        if (response.session.access_token.isBlank()) add(AuthBridgeContractIssue.MissingAccessToken)
        if (response.session.refresh_token.isBlank()) add(AuthBridgeContractIssue.MissingRefreshToken)
        if (response.session.expires_at != null && response.session.expires_at <= 0L) {
            add(AuthBridgeContractIssue.InvalidExpiry)
        }
    }
}

/** Safe diagnostic codes only; callers must not log tokens, credentials, phones, or profile ids. */
internal enum class AuthBridgeContractIssue {
    MissingProfileId,
    MissingAuthUserId,
    AuthUserIdMismatch,
    MissingAccessToken,
    MissingRefreshToken,
    InvalidExpiry
}
