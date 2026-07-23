package com.quata.core.moderation

import android.content.Context
import com.quata.core.config.AppConfig
import com.quata.core.session.SessionManager
import com.quata.data.supabase.SupabaseCommunityApi
import com.quata.feature.chat.data.ChatMessageStateWorkScheduler

class ModerationRepository(
    private val api: SupabaseCommunityApi,
    private val sessionManager: SessionManager,
    private val termsAcceptanceStore: UgcTermsAcceptanceStore,
    private val appContext: Context
) {
    suspend fun report(
        target: ModerationTarget,
        targetId: String,
        reason: ReportReason = ReportReason.Other,
        details: String? = null
    ): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) return@runCatching
        api.reportUgc(requireProfileId(), target.wireValue, targetId, reason.wireValue, details)
    }

    suspend fun blockProfile(profileId: String): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) return@runCatching
        api.blockProfile(requireProfileId(), profileId)
    }

    suspend fun hasAcceptedTerms(version: String = CurrentUgcTermsVersion): Result<Boolean> = runCatching {
        val userId = requireProfileId()
        if (termsAcceptanceStore.isAccepted(userId, version)) {
            if (termsAcceptanceStore.isPending(userId, version)) {
                ChatMessageStateWorkScheduler.scheduleOneTime(appContext)
            }
            return@runCatching true
        }
        val acceptedRemotely = if (AppConfig.USE_MOCK_BACKEND) true else api.hasAcceptedUgcTerms(userId, version)
        if (acceptedRemotely) termsAcceptanceStore.markAcceptedSynced(userId, version)
        acceptedRemotely
    }

    suspend fun acceptTerms(version: String = CurrentUgcTermsVersion): Result<Unit> = runCatching {
        val userId = requireProfileId()
        termsAcceptanceStore.markAcceptedPendingSync(userId, version)
        ChatMessageStateWorkScheduler.scheduleOneTime(appContext)
    }

    suspend fun flushPendingTermsForCurrentUser(version: String = CurrentUgcTermsVersion): Boolean {
        val userId = sessionManager.currentSession()?.userId ?: return true
        if (!termsAcceptanceStore.isPending(userId, version)) return true
        return runCatching {
            if (!AppConfig.USE_MOCK_BACKEND) api.acceptUgcTerms(userId, version)
            termsAcceptanceStore.markAcceptedSynced(userId, version)
        }.isSuccess
    }

    private fun requireProfileId(): String =
        requireNotNull(sessionManager.currentSession()?.userId) { "Authentication required" }

}
