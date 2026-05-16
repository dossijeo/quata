package com.quata.bettermessages

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.net.URLEncoder

class BetterMessagesRestApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json = BetterMessagesJson.default
) {
    private val restBase = "${baseUrl.ensureNoTrailingSlash()}/wp-json/better-messages/v1"
    private val booleanSerializer = Boolean.serializer()

    suspend fun getThread(threadId: Int, knownMessageIds: List<Int> = emptyList()): BmThreadResponse {
        return postJson(
            path = "/thread/$threadId",
            request = BmThreadRequest(messages = knownMessageIds),
            requestSerializer = BmThreadRequest.serializer(),
            responseSerializer = BmThreadResponse.serializer()
        )
    }

    suspend fun checkNew(
        lastUpdate: Long,
        visibleThreads: List<Int> = emptyList(),
        threadIds: List<Int> = emptyList()
    ): BmCheckNewResponse {
        return postJson(
            path = "/checkNew",
            request = BmCheckNewRequest(lastUpdate, visibleThreads, threadIds),
            requestSerializer = BmCheckNewRequest.serializer(),
            responseSerializer = BmCheckNewResponse.serializer()
        )
    }

    suspend fun sendMessage(
        threadId: Int,
        message: String,
        files: List<Int>? = null,
        replyToMessageId: Int? = null
    ): BmSendMessageResponse {
        return postJson(
            path = "/thread/$threadId/send",
            request = BmSendMessageRequest(
                message = message,
                files = files,
                meta = BmMessageMetaRequest(replyTo = replyToMessageId)
            ),
            requestSerializer = BmSendMessageRequest.serializer(),
            responseSerializer = BmSendMessageResponse.serializer()
        )
    }

    suspend fun sendReply(threadId: Int, message: String, replyToMessageId: Int): BmSendMessageResponse {
        return sendMessage(threadId, message, replyToMessageId = replyToMessageId)
    }

    suspend fun sendFiles(threadId: Int, fileIds: List<Int>, message: String = ""): BmSendMessageResponse {
        return sendMessage(threadId, message = message, files = fileIds)
    }

    suspend fun uploadFile(threadId: Int, file: File, mimeType: String = "application/octet-stream"): BmUploadResponse {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = file.name,
                body = file.asRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url("$restBase/thread/$threadId/upload".withNoCache())
            .post(body)
            .header("Accept", "application/json")
            .build()

        val text = client.executeSuspend(request).use { it.readBodyOrThrow() }
        return json.decodeFromString(BmUploadResponse.serializer(), text)
    }

    suspend fun forwardMessage(messageId: Int, threadIds: List<Int>): BmForwardResponse {
        return postJson(
            path = "/message/$messageId/forward",
            request = BmForwardRequest(threadIds),
            requestSerializer = BmForwardRequest.serializer(),
            responseSerializer = BmForwardResponse.serializer()
        )
    }

    suspend fun favoriteMessage(threadId: Int, messageId: Int): Boolean = favorite(threadId, messageId, "star")

    suspend fun unfavoriteMessage(threadId: Int, messageId: Int): Boolean = favorite(threadId, messageId, "unstar")

    private suspend fun favorite(threadId: Int, messageId: Int, type: String): Boolean {
        return postJson(
            path = "/thread/$threadId/favorite",
            request = BmFavoriteRequest(messageId, type),
            requestSerializer = BmFavoriteRequest.serializer(),
            responseSerializer = booleanSerializer
        )
    }

    suspend fun deleteMessages(threadId: Int, messageIds: List<Int>): BmThreadResponse {
        return postJson(
            path = "/thread/$threadId/deleteMessages",
            request = BmDeleteMessagesRequest(messageIds),
            requestSerializer = BmDeleteMessagesRequest.serializer(),
            responseSerializer = BmThreadResponse.serializer()
        )
    }

    suspend fun muteThread(threadId: Int): Boolean = postEmptyBoolean("/thread/$threadId/mute")

    suspend fun unmuteThread(threadId: Int): Boolean = postEmptyBoolean("/thread/$threadId/unmute")

    suspend fun searchSuggestions(search: String, threadId: Int? = null): List<BmUser> {
        val url = buildString {
            append("$restBase/suggestions?nocache=${System.currentTimeMillis()}")
            append("&search=").append(URLEncoder.encode(search, "UTF-8"))
            if (threadId != null) append("&threadId=").append(threadId)
        }

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        val text = client.executeSuspend(request).use { it.readBodyOrThrow() }
        return json.decodeFromString(ListSerializer(BmUser.serializer()), text)
    }

    suspend fun addParticipant(threadId: Int, userIds: List<Int>): Boolean {
        return postJson(
            path = "/thread/$threadId/addParticipant",
            request = BmAddParticipantRequest(userIds),
            requestSerializer = BmAddParticipantRequest.serializer(),
            responseSerializer = booleanSerializer
        )
    }

    suspend fun leaveThread(threadId: Int): Boolean = postEmptyBoolean("/thread/$threadId/leaveThread")

    suspend fun changeMeta(threadId: Int, key: String, value: String): Boolean {
        return postJson(
            path = "/thread/$threadId/changeMeta",
            request = BmChangeMetaRequest(key, value),
            requestSerializer = BmChangeMetaRequest.serializer(),
            responseSerializer = booleanSerializer
        )
    }

    suspend fun deleteThread(threadId: Int): Boolean = postEmptyAllowBlank("/thread/$threadId/delete")

    suspend fun restoreThread(threadId: Int): Boolean = postEmptyBoolean("/thread/$threadId/restore")

    private suspend fun postEmptyBoolean(path: String): Boolean {
        val request = Request.Builder()
            .url("$restBase$path".withNoCache())
            .post(ByteArray(0).toRequestBodyCompat())
            .defaultRestHeaders()
            .build()

        val text = client.executeSuspend(request).use { it.readBodyOrThrow() }
        return text.isBlank() || json.decodeFromString(booleanSerializer, text)
    }

    private suspend fun postEmptyAllowBlank(path: String): Boolean {
        val request = Request.Builder()
            .url("$restBase$path".withNoCache())
            .post(ByteArray(0).toRequestBodyCompat())
            .defaultRestHeaders()
            .build()

        client.executeSuspend(request).use { it.readBodyOrThrow() }
        return true
    }

    private suspend fun <Req, Res> postJson(
        path: String,
        request: Req,
        requestSerializer: KSerializer<Req>,
        responseSerializer: KSerializer<Res>
    ): Res {
        val bodyText = json.encodeToString(requestSerializer, request)
        val requestObj = Request.Builder()
            .url("$restBase$path".withNoCache())
            .post(bodyText.toRequestBodyCompat())
            .defaultRestHeaders()
            .build()

        val responseText = client.executeSuspend(requestObj).use { it.readBodyOrThrow() }
        return json.decodeFromString(responseSerializer, responseText)
    }
}
