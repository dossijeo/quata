package com.quata.bettermessages

class BetterMessagesRepository(
    private val client: BetterMessagesClient
) {
    suspend fun bootstrap(profileId: String): BmUnreadCountData {
        client.prepareSession(profileId)
        return client.bridge.getUnreadCount(profileId)
    }

    suspend fun openOrGetPrivateUrl(profileId: String, peerProfileId: String): BmUrlData {
        client.prepareSession(profileId)
        return client.bridge.getPrivateUrl(profileId, peerProfileId)
    }

    suspend fun loadThread(profileId: String, threadId: Int, knownMessageIds: List<Int> = emptyList()): BmThreadResponse {
        client.prepareSession(profileId)
        return client.rest.getThread(threadId, knownMessageIds)
    }

    suspend fun sendText(profileId: String, threadId: Int, text: String): BmSendMessageResponse {
        client.prepareSession(profileId)
        return client.rest.sendMessage(threadId, text)
    }

    suspend fun sendReply(profileId: String, threadId: Int, text: String, replyToMessageId: Int): BmSendMessageResponse {
        client.prepareSession(profileId)
        return client.rest.sendReply(threadId, text, replyToMessageId)
    }
}
