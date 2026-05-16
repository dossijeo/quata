package com.quata.bettermessages

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class BetterMessagesBridgeApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json = BetterMessagesJson.default
) {
    private val ajaxUrl = "${baseUrl.ensureNoTrailingSlash()}/wp-admin/admin-ajax.php"

    suspend fun setProfileContext(profileId: String): BmProfileContextData {
        return postAjax(
            serializer = BmProfileContextData.serializer(),
            "action" to "quqos_bm_set_profile_context",
            "profile_id" to profileId
        )
    }

    suspend fun syncSession(profileId: String): BmSyncSessionData {
        return postAjax(
            serializer = BmSyncSessionData.serializer(),
            "action" to "quqos_bm_sync_session",
            "profile_id" to profileId
        )
    }

    suspend fun getUnreadCount(profileId: String): BmUnreadCountData {
        return postAjax(
            serializer = BmUnreadCountData.serializer(),
            "action" to "quqos_bm_unread_count",
            "profile_id" to profileId
        )
    }

    suspend fun getInboxUrl(profileId: String): BmUrlData {
        return postAjax(
            serializer = BmUrlData.serializer(),
            "action" to "quqos_bm_inbox_url",
            "profile_id" to profileId
        )
    }

    suspend fun getPrivateUrl(profileId: String, peerProfileId: String): BmUrlData {
        return postAjax(
            serializer = BmUrlData.serializer(),
            "action" to "quqos_bm_private_url",
            "profile_id" to profileId,
            "target_profile_id" to peerProfileId
        )
    }

    suspend fun getCommunityUrl(profileId: String, threadId: Int? = null): BmUrlData {
        return postAjax(
            serializer = BmUrlData.serializer(),
            "action" to "quqos_bm_community_url",
            "profile_id" to profileId,
            "thread_id" to threadId?.toString()
        )
    }

    suspend fun sendSos(profileId: String, message: String): BmSendSosData {
        return postAjax(
            serializer = BmSendSosData.serializer(),
            "action" to "quqos_bm_send_sos",
            "profile_id" to profileId,
            "message" to message
        )
    }

    private suspend fun <T> postAjax(
        serializer: KSerializer<T>,
        vararg fields: Pair<String, String?>
    ): T {
        val request = Request.Builder()
            .url(ajaxUrl)
            .post(formBody(*fields))
            .defaultAjaxHeaders()
            .build()

        val responseText = client.executeSuspend(request).use { it.readBodyOrThrow() }
        val wrapperSerializer = WpAjaxResponse.serializer(serializer)
        val wrapper = json.decodeFromString(wrapperSerializer, responseText)
        if (!wrapper.success) {
            throw BetterMessagesBridgeException("WordPress AJAX returned success=false: ${responseText.take(500)}")
        }
        return wrapper.data ?: throw BetterMessagesBridgeException("WordPress AJAX returned empty data: ${responseText.take(500)}")
    }
}

class BetterMessagesBridgeException(message: String) : IllegalStateException(message)
