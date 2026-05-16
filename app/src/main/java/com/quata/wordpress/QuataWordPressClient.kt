package com.quata.wordpress

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class QuataWordPressClient(
    baseUrl: String,
    private val httpClient: OkHttpClient = defaultClient()
) {
    private val rootUrl: String = baseUrl.trimEnd('/') + "/"
    private val ajaxUrl: String = rootUrl + "wp-admin/admin-ajax.php"
    private val uploadVideoRestUrl: String = rootUrl + "wp-json/quqos/v1/upload-video"

    suspend fun checkRegistrationGuard(
        deviceId: String,
        displayName: String,
        barrio: String,
        phone: String,
        phoneLocal: String,
        countryCode: String,
        url: String
    ): AjaxEnvelope<RegistrationGuardData> {
        val json = postAjax(
            action = "quqos_check_registration_guard",
            params = mapOf(
                "device_id" to deviceId,
                "display_name" to displayName,
                "barrio" to barrio,
                "phone" to phone,
                "phone_local" to phoneLocal,
                "country_code" to countryCode,
                "url" to url
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = RegistrationGuardData(
                blocked = JsonLite.bool(dataJson, "blocked") == true,
                message = JsonLite.string(dataJson, "message"),
                matchCount = JsonLite.int(dataJson, "match_count") ?: 0
            ),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    suspend fun recordRegistrationGuard(
        deviceId: String,
        profileId: String,
        displayName: String,
        barrio: String,
        phone: String,
        phoneLocal: String,
        countryCode: String,
        url: String
    ): AjaxEnvelope<RegistrationRecordData> {
        val json = postAjax(
            action = "quqos_record_registration_guard",
            params = mapOf(
                "device_id" to deviceId,
                "profile_id" to profileId,
                "display_name" to displayName,
                "barrio" to barrio,
                "phone" to phone,
                "phone_local" to phoneLocal,
                "country_code" to countryCode,
                "url" to url
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = RegistrationRecordData(
                matchCount = JsonLite.int(dataJson, "match_count") ?: 0,
                rawJson = dataJson
            ),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    suspend fun requestPasswordRecovery(
        displayName: String,
        phone: String,
        contactMethod: String,
        message: String,
        deviceId: String,
        url: String
    ): AjaxEnvelope<PasswordRecoveryData> {
        val json = postAjax(
            action = "quqos_password_recovery_request",
            params = mapOf(
                "display_name" to displayName,
                "phone" to phone,
                "contact_method" to contactMethod,
                "message" to message,
                "device_id" to deviceId,
                "url" to url
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = PasswordRecoveryData(
                message = JsonLite.string(dataJson, "message"),
                rawJson = dataJson
            ),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    suspend fun moderateContent(
        context: String,
        text: String,
        imageName: String = "",
        imageType: String = "",
        imageScore: Int = 0,
        displayName: String = "",
        profileId: String = "",
        url: String = ""
    ): AjaxEnvelope<ModerationData> {
        val json = postAjax(
            action = "quqos_moderate_content",
            params = mapOf(
                "context" to context,
                "text" to text,
                "image_name" to imageName,
                "image_type" to imageType,
                "image_score" to imageScore.toString(),
                "display_name" to displayName,
                "profile_id" to profileId,
                "url" to url
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = ModerationData(
                action = JsonLite.string(dataJson, "action") ?: "allow",
                reason = JsonLite.string(dataJson, "reason"),
                score = JsonLite.int(dataJson, "score") ?: 0,
                message = JsonLite.string(dataJson, "message"),
                rawJson = dataJson
            ),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    suspend fun reportClientError(
        rawMessage: String,
        friendlyMessage: String,
        category: String,
        source: String = "android",
        context: String = "",
        url: String = "",
        profileId: String = "",
        displayName: String = "",
        userAgent: String = "Qüata Android"
    ): AjaxEnvelope<ErrorReportData> {
        val json = postAjax(
            action = "quqos_report_client_error",
            params = mapOf(
                "raw_message" to rawMessage,
                "friendly_message" to friendlyMessage,
                "category" to category,
                "source" to source,
                "context" to context,
                "url" to url,
                "profile_id" to profileId,
                "display_name" to displayName,
                "ua" to userAgent
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = ErrorReportData(rawJson = dataJson),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    suspend fun trackVisit(
        profileId: String,
        displayName: String,
        barrio: String,
        url: String,
        referrer: String = "",
        visitorId: String,
        pageTitle: String,
        language: String,
        timezone: String,
        screen: String,
        platform: String = "Android"
    ): AjaxEnvelope<TrackVisitData> {
        val json = postAjax(
            action = "quqos_track_visit",
            params = mapOf(
                "profile_id" to profileId,
                "display_name" to displayName,
                "barrio" to barrio,
                "url" to url,
                "referrer" to referrer,
                "visitor_id" to visitorId,
                "page_title" to pageTitle,
                "language" to language,
                "timezone" to timezone,
                "screen" to screen,
                "platform" to platform,
                "source" to "android"
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = TrackVisitData(rawJson = dataJson),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    /**
     * Preferido para Android. Endpoint REST:
     * POST /wp-json/quqos/v1/upload-video
     * form-data: video=<file>
     */
    suspend fun uploadPostVideoRest(
        fileName: String,
        bytes: ByteArray,
        mimeType: String
    ): AjaxEnvelope<VideoUploadData> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "video",
                fileName,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(uploadVideoRestUrl)
            .post(body)
            .build()

        val json = execute(request)
        if (json.contains(""""message"""") && json.contains(""""code"""")) {
            return AjaxEnvelope(
                success = false,
                data = VideoUploadData(rawJson = json),
                errorMessage = JsonLite.string(json, "message"),
                rawJson = json
            )
        }

        return AjaxEnvelope(
            success = true,
            data = VideoUploadData(
                url = JsonLite.string(json, "url"),
                size = JsonLite.long(json, "size"),
                mime = JsonLite.string(json, "mime"),
                file = JsonLite.string(json, "file"),
                rawJson = json
            ),
            rawJson = json
        )
    }

    /**
     * Alternativa equivalente al frontend web:
     * POST /wp-admin/admin-ajax.php?action=quqos_upload_post_video
     */
    suspend fun uploadPostVideoAjax(
        fileName: String,
        bytes: ByteArray,
        mimeType: String
    ): AjaxEnvelope<VideoUploadData> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", "quqos_upload_post_video")
            .addFormDataPart(
                "video",
                fileName,
                bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            )
            .build()

        val request = Request.Builder()
            .url(ajaxUrl)
            .post(body)
            .build()

        val json = execute(request)
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = VideoUploadData(
                url = JsonLite.string(dataJson, "url"),
                size = JsonLite.long(dataJson, "size"),
                mime = JsonLite.string(dataJson, "mime"),
                file = JsonLite.string(dataJson, "file"),
                rawJson = dataJson
            ),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    /**
     * Opcional: estos endpoints existen en WordPress, aunque también pueden hacerse desde Supabase.
     */
    suspend fun getProfileFollowState(
        profileId: String,
        targetProfileId: String
    ): AjaxEnvelope<ProfileFollowStateData> {
        val json = postAjax(
            action = "quqos_profile_follow_state",
            params = mapOf(
                "profile_id" to profileId,
                "target_profile_id" to targetProfileId
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = ProfileFollowStateData(
                isFollowing = JsonLite.bool(dataJson, "is_following") == true,
                rawJson = dataJson
            ),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    suspend fun getProfileFollowStats(targetProfileId: String): AjaxEnvelope<ProfileFollowStatsData> {
        val json = postAjax(
            action = "quqos_profile_follow_stats",
            params = mapOf("target_profile_id" to targetProfileId)
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = ProfileFollowStatsData(
                followers = JsonLite.int(dataJson, "followers") ?: 0,
                following = JsonLite.int(dataJson, "following") ?: 0,
                rawJson = dataJson
            ),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    suspend fun getProfileFollowConnections(
        targetProfileId: String,
        mode: String // followers | following
    ): AjaxEnvelope<ProfileFollowConnectionsData> {
        val json = postAjax(
            action = "quqos_profile_follow_connections",
            params = mapOf(
                "target_profile_id" to targetProfileId,
                "mode" to mode
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = ProfileFollowConnectionsData(rawJson = dataJson),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    suspend fun toggleProfileFollow(
        profileId: String,
        targetProfileId: String,
        follow: Boolean
    ): AjaxEnvelope<ProfileFollowToggleData> {
        val json = postAjax(
            action = "quqos_profile_follow_toggle",
            params = mapOf(
                "profile_id" to profileId,
                "target_profile_id" to targetProfileId,
                "follow" to if (follow) "1" else "0"
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = ProfileFollowToggleData(
                isFollowing = JsonLite.bool(dataJson, "is_following") == true,
                followers = JsonLite.int(dataJson, "followers") ?: 0,
                myFollowing = JsonLite.int(dataJson, "my_following") ?: 0,
                rawJson = dataJson
            ),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    /**
     * Web-only: NO recomendado para app Android nativa.
     * Este método queda documentado para mantener paridad, pero en Android usaremos FCM aparte.
     */
    suspend fun saveWebPushSubscription(profileId: String, subscriptionJson: String): AjaxEnvelope<TrackVisitData> {
        val json = postAjax(
            action = "quqos_save_push_subscription",
            params = mapOf(
                "profile_id" to profileId,
                "subscription" to subscriptionJson
            )
        )
        val dataJson = JsonLite.objectBody(json, "data") ?: "{}"
        return AjaxEnvelope(
            success = JsonLite.bool(json, "success") == true,
            data = TrackVisitData(rawJson = dataJson),
            errorMessage = extractWordPressError(json),
            rawJson = json
        )
    }

    private suspend fun postAjax(
        action: String,
        params: Map<String, String>
    ): String {
        val form = FormBody.Builder()
            .add("action", action)
            .apply {
                params.forEach { (key, value) -> add(key, value) }
            }
            .build()

        val request = Request.Builder()
            .url(ajaxUrl)
            .post(form)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        return execute(request)
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(1600)}")
            }
            body
        }
    }

    private fun extractWordPressError(json: String): String? {
        if (JsonLite.bool(json, "success") != false) return null
        val dataJson = JsonLite.objectBody(json, "data") ?: json
        return JsonLite.string(dataJson, "message") ?: JsonLite.string(json, "message")
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
