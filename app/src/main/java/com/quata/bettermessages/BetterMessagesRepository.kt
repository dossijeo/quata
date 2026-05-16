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

    suspend fun setProfileContext(profileId: String): BmProfileContextData {
        return client.bridge.setProfileContext(profileId)
    }

    suspend fun getUnreadCount(profileId: String): BmUnreadCountData {
        return client.bridge.getUnreadCount(profileId)
    }

    suspend fun prepareBridgeContext(profileId: String): BmInboxSessionData {
        setProfileContext(profileId)
        val unread = getUnreadCount(profileId)
        return BmInboxSessionData(
            currentWpUserId = unread.userId,
            unreadTotal = unread.unreadTotal
        )
    }

    suspend fun refreshRestNonce(profileId: String): String? {
        return client.refreshRestNonce(profileId)
    }

    suspend fun checkNewInCurrentSession(
        lastUpdate: Long,
        visibleThreads: List<Int> = emptyList(),
        threadIds: List<Int> = emptyList()
    ): BmCheckNewResponse {
        return client.rest.checkNew(lastUpdate, visibleThreads, threadIds)
    }

    suspend fun checkNew(
        profileId: String,
        lastUpdate: Long,
        visibleThreads: List<Int> = emptyList(),
        threadIds: List<Int> = emptyList()
    ): BmCheckNewResponse {
        return try {
            checkNewInCurrentSession(lastUpdate, visibleThreads, threadIds)
        } catch (error: BetterMessagesHttpException) {
            if (error.statusCode != 401 && error.statusCode != 403) throw error
            refreshRestNonce(profileId)
            checkNewInCurrentSession(lastUpdate, visibleThreads, threadIds)
        }
    }

    suspend fun loadInbox(profileId: String): BmInboxSnapshot {
        val session = prepareBridgeContext(profileId)
        refreshRestNonce(profileId)
        return BmInboxSnapshot(
            currentWpUserId = session.currentWpUserId,
            response = checkNewInCurrentSession(lastUpdate = 0)
        )
    }

    suspend fun loadThreadInCurrentSession(threadId: Int, knownMessageIds: List<Int> = emptyList()): BmThreadResponse {
        return client.rest.getThread(threadId, knownMessageIds)
    }

    suspend fun openOrGetPrivateUrl(profileId: String, peerProfileId: String): BmUrlData {
        prepareRestSession(profileId)
        return client.bridge.getPrivateUrl(profileId, peerProfileId)
    }

    suspend fun loadThread(profileId: String, threadId: Int, knownMessageIds: List<Int> = emptyList()): BmThreadResponse {
        prepareRestSession(profileId)
        return client.rest.getThread(threadId, knownMessageIds)
    }

    suspend fun sendText(profileId: String, threadId: Int, text: String): BmSendMessageResponse {
        prepareRestSession(profileId)
        return client.rest.sendMessage(threadId, text)
    }

    suspend fun sendReply(profileId: String, threadId: Int, text: String, replyToMessageId: Int): BmSendMessageResponse {
        prepareRestSession(profileId)
        return client.rest.sendReply(threadId, text, replyToMessageId)
    }

    suspend fun uploadFile(profileId: String, threadId: Int, file: File, mimeType: String): BmUploadResponse {
        prepareRestSession(profileId)
        return client.rest.uploadFile(threadId, file, mimeType)
    }

    suspend fun sendFiles(profileId: String, threadId: Int, fileIds: List<Int>, message: String = ""): BmSendMessageResponse {
        prepareRestSession(profileId)
        return client.rest.sendFiles(threadId, fileIds, message)
    }

    suspend fun forwardMessage(profileId: String, messageId: Int, threadIds: List<Int>): BmForwardResponse {
        prepareRestSession(profileId)
        return client.rest.forwardMessage(messageId, threadIds)
    }

    suspend fun favoriteMessage(profileId: String, threadId: Int, messageId: Int, favorite: Boolean): Boolean {
        prepareRestSession(profileId)
        return if (favorite) client.rest.favoriteMessage(threadId, messageId) else client.rest.unfavoriteMessage(threadId, messageId)
    }

    suspend fun deleteMessages(profileId: String, threadId: Int, messageIds: List<Int>): BmThreadResponse {
        prepareRestSession(profileId)
        return client.rest.deleteMessages(threadId, messageIds)
    }

    suspend fun muteThread(profileId: String, threadId: Int, muted: Boolean): Boolean {
        prepareRestSession(profileId)
        return if (muted) client.rest.muteThread(threadId) else client.rest.unmuteThread(threadId)
    }

    suspend fun addParticipant(profileId: String, threadId: Int, wpUserIds: List<Int>): Boolean {
        prepareRestSession(profileId)
        return client.rest.addParticipant(threadId, wpUserIds)
    }

    suspend fun leaveThread(profileId: String, threadId: Int): Boolean {
        prepareRestSession(profileId)
        return client.rest.leaveThread(threadId)
    }

    suspend fun deleteThread(profileId: String, threadId: Int): Boolean {
        prepareRestSession(profileId)
        return client.rest.deleteThread(threadId)
    }

    suspend fun restoreThread(profileId: String, threadId: Int): Boolean {
        prepareRestSession(profileId)
        return client.rest.restoreThread(threadId)
    }

    suspend fun sendSos(profileId: String, message: String): BmSendSosData {
        client.prepareSession(profileId)
        return client.bridge.sendSos(profileId, message)
    }

    private suspend fun prepareRestSession(profileId: String): Int? {
        val session = prepareBridgeContext(profileId)
        refreshRestNonce(profileId)
        return session.currentWpUserId
    }
}

data class BmInboxSnapshot(
    val currentWpUserId: Int?,
    val response: BmCheckNewResponse
)

data class BmInboxSessionData(
    val currentWpUserId: Int?,
    val unreadTotal: Int
)
