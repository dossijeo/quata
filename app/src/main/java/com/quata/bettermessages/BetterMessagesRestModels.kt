package com.quata.bettermessages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BmThreadResponse(
    val threads: List<BmThread> = emptyList(),
    val users: List<BmUser> = emptyList(),
    val messages: List<BmMessage> = emptyList(),
    val serverTime: Long? = null
)

@Serializable
data class BmCheckNewRequest(
    val lastUpdate: Long,
    val visibleThreads: List<Int> = emptyList(),
    val threadIds: List<Int> = emptyList()
)

@Serializable
data class BmCheckNewResponse(
    val users: List<BmUser> = emptyList(),
    val messages: List<BmMessage> = emptyList(),
    val threads: List<BmThread> = emptyList(),
    val currentTime: Long? = null
)

@Serializable
data class BmThreadRequest(
    val messages: List<Int> = emptyList()
)

@Serializable
data class BmPrivateThreadRequest(
    @SerialName("user_id") val userId: Int,
    val create: Boolean
)

@Serializable
data class BmSendMessageRequest(
    val message: String,
    val files: List<Int>? = null,
    val meta: BmMessageMetaRequest
)

@Serializable
data class BmMessageMetaRequest(
    @SerialName("reply_to") val replyTo: Int? = null
)

@Serializable
data class BmSendMessageResponse(
    val result: Boolean,
    @SerialName("message_id") val messageId: Int? = null,
    @SerialName("thread_id") val threadId: Int? = null,
    val redirect: Boolean? = null,
    val update: BmThreadResponse? = null
)

@Serializable
data class BmUploadResponse(
    val result: Boolean? = null,
    val error: String? = null,
    val id: Int? = null
)

@Serializable
data class BmForwardRequest(
    @SerialName("thread_ids") val threadIds: List<Int>
)

@Serializable
data class BmForwardResponse(
    val result: Boolean = false,
    val sent: Map<String, Int> = emptyMap(),
    val errors: List<String> = emptyList()
)

@Serializable
data class BmFavoriteRequest(
    val messageId: Int,
    val type: String
)

@Serializable
data class BmDeleteMessagesRequest(
    val messageIds: List<Int>
)

@Serializable
data class BmSaveMessageRequest(
    @SerialName("message_id") val messageId: Int,
    val message: String
)

@Serializable
data class BmAddParticipantRequest(
    @SerialName("user_id") val userId: List<Int>
)

@Serializable
data class BmModeratorRequest(
    @SerialName("user_id") val userId: Int
)

@Serializable
data class BmChangeMetaRequest(
    val key: String,
    val value: String
)

@Serializable
data class BmThread(
    @SerialName("thread_id") val threadId: Int,
    val isHidden: Int? = null,
    val isDeleted: Int? = null,
    val type: String? = null,
    val title: String? = null,
    val subject: String? = null,
    val image: String? = null,
    val lastTime: Long? = null,
    val participants: List<Int> = emptyList(),
    val participantsCount: Int? = null,
    val moderators: List<Int> = emptyList(),
    val url: String? = null,
    val meta: BmThreadMeta? = null,
    val isPinned: Int? = null,
    val isMuted: Boolean? = null,
    val permissions: BmPermissions? = null,
    val mentions: List<JsonElement> = emptyList(),
    val unread: Int? = null
)

@Serializable
data class BmThreadMeta(
    val allowInvite: Boolean? = null
)

@Serializable
data class BmPermissions(
    val isModerator: Boolean? = null,
    val deleteAllowed: Boolean? = null,
    val canDeleteOwnMessages: Boolean? = null,
    val canDeleteAllMessages: Boolean? = null,
    val canEditOwnMessages: Boolean? = null,
    val canEditAllMessages: Boolean? = null,
    val canFavorite: Boolean? = null,
    val canMuteThread: Boolean? = null,
    val canEraseThread: Boolean? = null,
    val canClearThread: Boolean? = null,
    val canInvite: Boolean? = null,
    val canLeave: Boolean? = null,
    val canUpload: Boolean? = null,
    val canVideoCall: Boolean? = null,
    val canAudioCall: Boolean? = null,
    val canMaximize: Boolean? = null,
    val canPinMessages: Boolean? = null,
    val canMinimize: Boolean? = null,
    val canReply: Boolean? = null,
    val canReplyMsg: List<Int> = emptyList(),
    val requireModeration: Boolean? = null,
    val preventVoiceMessages: Boolean? = null,
    val canBlockUser: Boolean? = null
)

@Serializable
data class BmUser(
    val id: String,
    @SerialName("user_id") val userId: Int? = null,
    val name: String? = null,
    val avatar: String? = null,
    val url: JsonElement? = null,
    val verified: Int? = null,
    val lastActive: String? = null,
    val isFriend: Int? = null,
    val canVideo: Int? = null,
    val canAudio: Int? = null,
    val blocked: Int? = null,
    val canBlock: Int? = null
)

@Serializable
data class BmMessage(
    @SerialName("thread_id") val threadId: Int,
    @SerialName("sender_id") val senderId: Int,
    val message: String,
    val created_at: Long,
    val updated_at: Long? = null,
    val temp_id: String? = null,
    @SerialName("message_id") val messageId: Int,
    val meta: BmMessageMeta = BmMessageMeta(),
    val favorited: Int? = null
)

@Serializable
data class BmMessageMeta(
    val reactions: List<JsonElement> = emptyList(),
    val files: List<BmFile> = emptyList(),
    val replyTo: Int? = null,
    val forwardedFrom: Int? = null,
    val forwardedFromUser: Int? = null
)

@Serializable
data class BmFile(
    val id: Int,
    val thumb: JsonElement? = null,
    val url: String? = null,
    val mimeType: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val ext: String? = null
)
