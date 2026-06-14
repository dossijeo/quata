package com.quata.bettermessages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class BmThreadResponse(
    @Serializable(with = FlexibleBmThreadListSerializer::class)
    val threads: List<BmThread> = emptyList(),
    @Serializable(with = FlexibleBmUserListSerializer::class)
    val users: List<BmUser> = emptyList(),
    @Serializable(with = FlexibleBmMessageListSerializer::class)
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
    @Serializable(with = FlexibleBmUserListSerializer::class)
    val users: List<BmUser> = emptyList(),
    @Serializable(with = FlexibleBmMessageListSerializer::class)
    val messages: List<BmMessage> = emptyList(),
    @Serializable(with = FlexibleBmThreadListSerializer::class)
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
data class BmNewThreadRequest(
    val recipients: List<Int>,
    val subject: String? = null,
    val type: String? = null,
    val message: String = "",
    val files: List<Int> = emptyList(),
    val meta: BmNewThreadMetaRequest = BmNewThreadMetaRequest()
)

@Serializable
data class BmNewThreadMetaRequest(
    @SerialName("unique_key") val uniqueKey: String? = null
)

@Serializable
data class BmNewThreadResponse(
    val result: Boolean? = null,
    @SerialName("message_id") val messageId: Int? = null,
    @SerialName("thread_id") val threadId: Int? = null,
    val redirect: Boolean? = null,
    val update: BmThreadResponse? = null,
    @Serializable(with = FlexibleBmThreadListSerializer::class)
    val threads: List<BmThread> = emptyList(),
    @Serializable(with = FlexibleBmUserListSerializer::class)
    val users: List<BmUser> = emptyList(),
    @Serializable(with = FlexibleBmMessageListSerializer::class)
    val messages: List<BmMessage> = emptyList(),
    val serverTime: Long? = null
) {
    fun threadResponse(): BmThreadResponse =
        update ?: BmThreadResponse(
            threads = threads,
            users = users,
            messages = messages,
            serverTime = serverTime
        )
}

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
data class BmThreadAttachmentsResponse(
    @Serializable(with = FlexibleThreadAttachmentFileListSerializer::class)
    val files: List<BmThreadAttachmentFile> = emptyList(),
    val hasMore: Boolean = false,
    val page: Int = 1,
    val counts: BmThreadAttachmentCounts = BmThreadAttachmentCounts(),
    val activeType: String? = null
)

@Serializable
data class BmThreadAttachmentFile(
    val id: Int,
    val url: String? = null,
    val thumb: JsonElement? = null,
    val mimeType: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val ext: String? = null,
    val date: String? = null,
    val messageId: Int? = null
)

@Serializable
data class BmThreadAttachmentCounts(
    val photos: Int = 0,
    val videos: Int = 0,
    val audio: Int = 0,
    val files: Int = 0
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
data class BmChangeSubjectRequest(
    val subject: String
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
    @Serializable(with = FlexibleIntListSerializer::class)
    val participants: List<Int> = emptyList(),
    val participantsCount: Int? = null,
    @Serializable(with = FlexibleIntListSerializer::class)
    val moderators: List<Int> = emptyList(),
    val url: String? = null,
    @Serializable(with = FlexibleBmThreadMetaSerializer::class)
    val meta: BmThreadMeta? = null,
    val isPinned: Int? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val isMuted: Boolean? = null,
    @Serializable(with = FlexibleBmPermissionsSerializer::class)
    val permissions: BmPermissions? = null,
    @Serializable(with = FlexibleJsonElementListSerializer::class)
    val mentions: List<JsonElement> = emptyList(),
    val unread: Int? = null
)

@Serializable
data class BmThreadMeta(
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val allowInvite: Boolean? = null
)

@Serializable
data class BmPermissions(
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val isModerator: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val deleteAllowed: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canDeleteOwnMessages: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canDeleteAllMessages: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canEditOwnMessages: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canEditAllMessages: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canFavorite: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canMuteThread: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canEraseThread: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canClearThread: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canInvite: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canLeave: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canUpload: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canVideoCall: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canAudioCall: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canMaximize: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canPinMessages: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canMinimize: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canReply: Boolean? = null,
    @Serializable(with = FlexibleIntListSerializer::class)
    val canReplyMsg: List<Int> = emptyList(),
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val requireModeration: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val preventVoiceMessages: Boolean? = null,
    @Serializable(with = FlexibleNullableBooleanSerializer::class)
    val canBlockUser: Boolean? = null
)

@Serializable
data class BmUser(
    @Serializable(with = FlexibleStringSerializer::class)
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
    @Serializable(with = FlexibleBmMessageMetaSerializer::class)
    val meta: BmMessageMeta = BmMessageMeta(),
    val favorited: Int? = null
)

@Serializable
data class BmMessageMeta(
    @Serializable(with = FlexibleJsonElementListSerializer::class)
    val reactions: List<JsonElement> = emptyList(),
    @Serializable(with = FlexibleBmFileListSerializer::class)
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
