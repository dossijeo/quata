package com.quata.feature.chat.presentation.conversations

import com.quata.core.platform.PreferenceStore

/** Actor-scoped persistence for the inbox filter across a real process relaunch. */
class ConversationSearchPreferences(
    private val store: PreferenceStore,
) {
    suspend fun restore(actorProfileId: String): String =
        store.getString(key(actorProfileId)).orEmpty().take(MaxQueryLength)

    suspend fun persist(actorProfileId: String, query: String) {
        val bounded = query.take(MaxQueryLength)
        if (bounded.isBlank()) store.remove(key(actorProfileId))
        else store.putString(key(actorProfileId), bounded)
    }

    private fun key(actorProfileId: String): String = "$KeyPrefix$actorProfileId"

    companion object {
        const val MaxQueryLength = 160
        private const val KeyPrefix = "quata.chat.conversations.search.v1."
    }
}
