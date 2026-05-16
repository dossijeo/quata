package com.quata.bettermessages

import java.io.File

class BetterMessagesRepository(
    private val client: BetterMessagesClient
) {
    suspend fun syncSession(profileId: String): BmSyncSessionData {
        return client.prepareSession(profileId)
    }

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

    suspend fun uploadFile(profileId: String, threadId: Int, file: File, mimeType: String): BmUploadResponse {
        client.prepareSession(profileId)
        return client.rest.uploadFile(threadId, file, mimeType)
    }

    suspend fun sendFiles(profileId: String, threadId: Int, fileIds: List<Int>, message: String = ""): BmSendMessageResponse {
        client.prepareSession(profileId)
        return client.rest.sendFiles(threadId, fileIds, message)
    }

    suspend fun forwardMessage(profileId: String, messageId: Int, threadIds: List<Int>): BmForwardResponse {
        client.prepareSession(profileId)
        return client.rest.forwardMessage(messageId, threadIds)
    }

    suspend fun favoriteMessage(profileId: String, threadId: Int, messageId: Int, favorite: Boolean): Boolean {
        client.prepareSession(profileId)
        return if (favorite) client.rest.favoriteMessage(threadId, messageId) else client.rest.unfavoriteMessage(threadId, messageId)
    }

    suspend fun deleteMessages(profileId: String, threadId: Int, messageIds: List<Int>): BmThreadResponse {
        client.prepareSession(profileId)
        return client.rest.deleteMessages(threadId, messageIds)
    }

    suspend fun muteThread(profileId: String, threadId: Int, muted: Boolean): Boolean {
        client.prepareSession(profileId)
        return if (muted) client.rest.muteThread(threadId) else client.rest.unmuteThread(threadId)
    }

    suspend fun addParticipant(profileId: String, threadId: Int, wpUserIds: List<Int>): Boolean {
        client.prepareSession(profileId)
        return client.rest.addParticipant(threadId, wpUserIds)
    }

    suspend fun leaveThread(profileId: String, threadId: Int): Boolean {
        client.prepareSession(profileId)
        return client.rest.leaveThread(threadId)
    }

    suspend fun deleteThread(profileId: String, threadId: Int): Boolean {
        client.prepareSession(profileId)
        return client.rest.deleteThread(threadId)
    }

    suspend fun restoreThread(profileId: String, threadId: Int): Boolean {
        client.prepareSession(profileId)
        return client.rest.restoreThread(threadId)
    }

    suspend fun sendSos(profileId: String, message: String): BmSendSosData {
        client.prepareSession(profileId)
        return client.bridge.sendSos(profileId, message)
    }
}
