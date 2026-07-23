package com.quata.feature.chat.presentation.chat

import kotlin.random.Random

/** UI copy requested by shared chat presentation logic. Each platform resolves it locally. */
enum class ChatText {
    LoadConversations,
    LoadMessages,
    Send,
    You,
    Update,
    AddParticipant,
    AddParticipants,
    LoadCandidates,
    DeleteConversation,
    LeaveConversation,
    PromoteParticipant,
    DemoteParticipant,
    RemoveParticipant,
    BlockParticipant,
    UpdateFavorite,
    DeleteMessage,
    ReportSent,
    ReportMessage,
    Forward,
    RestoreConversation,
    OpenConversation
}

internal fun newClientMessageId(): String =
    "${currentEpochMillis()}-${Random.nextLong().toString(16)}"

internal fun String.stripMarkup(): String =
    replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

expect fun currentEpochMillis(): Long
