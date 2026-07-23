package com.quata.core.moderation

enum class ModerationTarget(val wireValue: String) {
    CommunityPost("community_post"),
    OfficialPost("official_post"),
    CommunityComment("community_comment"),
    OfficialComment("official_comment"),
    ChatMessage("chat_message"),
    Profile("profile"),
}

enum class ReportReason(val wireValue: String) {
    ChildSafety("child_safety"),
    Harassment("harassment"),
    Hate("hate"),
    Sexual("sexual"),
    Violence("violence"),
    Spam("spam"),
    Impersonation("impersonation"),
    Other("other"),
}

const val CurrentUgcTermsVersion = "2026-07"
