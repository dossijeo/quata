package com.quata.core.moderation

import com.quata.core.config.AppConfig
import com.quata.core.session.SessionManager
import com.quata.data.supabase.SupabaseCommunityApi

enum class ModerationTarget(val wireValue: String) {
    CommunityPost("community_post"),
    OfficialPost("official_post"),
    CommunityComment("community_comment"),
    OfficialComment("official_comment"),
    ChatMessage("chat_message"),
    Profile("profile")
}

enum class ReportReason(val wireValue: String) {
    ChildSafety("child_safety"),
    Harassment("harassment"),
    Hate("hate"),
    Sexual("sexual"),
    Violence("violence"),
    Spam("spam"),
    Impersonation("impersonation"),
    Other("other")
}

class ModerationRepository(
    private val api: SupabaseCommunityApi,
    private val sessionManager: SessionManager
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

    suspend fun hasAcceptedTerms(version: String = CURRENT_TERMS_VERSION): Result<Boolean> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) true else api.hasAcceptedUgcTerms(requireProfileId(), version)
    }

    suspend fun acceptTerms(version: String = CURRENT_TERMS_VERSION): Result<Unit> = runCatching {
        if (AppConfig.USE_MOCK_BACKEND) return@runCatching
        api.acceptUgcTerms(requireProfileId(), version)
    }

    private fun requireProfileId(): String =
        requireNotNull(sessionManager.currentSession()?.userId) { "Authentication required" }

    companion object {
        const val CURRENT_TERMS_VERSION = "2026-07"
    }
}
