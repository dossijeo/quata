package com.quata.web

import com.quata.core.text.buildPostBodyWithMeta
import com.quata.feature.postcomposer.domain.PostComposerDraft
import com.quata.feature.postcomposer.domain.PostComposerRepository
import com.quata.feature.postcomposer.domain.PostComposerType
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Browser publication adapter. It uses the authenticated Supabase session held by the Web
 * launcher, uploads browser Blob URLs to the existing `community-posts` bucket and then creates
 * the same `community_posts` shape consumed by the common Feed repository.
 */
class WebPostComposerRepository(
    private val configuration: WebRuntimeConfiguration,
    private val authRepository: WebAuthRepository,
    private val client: WebPostgrestClient,
) : PostComposerRepository {
    override suspend fun createPost(draft: PostComposerDraft): Result<String?> = runCatching {
        validate(draft)
        val profileId = authRepository.restoreLocalSession()?.userId
            ?: error("web_session_missing")
        val mediaUrl = when (draft.type) {
            PostComposerType.Text -> null
            PostComposerType.Image -> uploadMedia(profileId, draft.imageUri ?: error("web_composer_image_missing"), "image")
            PostComposerType.Video -> uploadMedia(profileId, draft.videoUri ?: error("web_composer_video_missing"), "video")
        }
        val response = createCommunityPost(
            profileId = profileId,
            wallId = resolveWallId(profileId),
            body = draft.toRemoteBody(),
            imageUrl = mediaUrl.takeIf { draft.type == PostComposerType.Image },
            videoUrl = mediaUrl.takeIf { draft.type == PostComposerType.Video },
        )
        Json.parseToJsonElement(response).jsonArray.firstOrNull()
            ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    private suspend fun resolveWallId(profileId: String): String {
        client.webComposerRows("community_members", mapOf("select" to "wall_id", "profile_id" to "eq.$profileId"), 1)
            .firstOrNull()?.get("wall_id")?.jsonPrimitive?.contentOrNull?.let { return it }
        return client.webComposerRows(
            "community_walls_stats",
            mapOf("select" to "id", "is_active" to "eq.true", "order" to "sort_order.asc,created_at.desc"),
            1,
        ).firstOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
            ?: error("web_composer_active_wall_missing")
    }

    private suspend fun uploadMedia(profileId: String, reference: String, kind: String): String {
        val baseUrl = configuration.supabaseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: error("supabase_url_missing")
        val credentials = authRepository.currentWebPushCredentials() ?: error("web_session_missing")
        val apiKey = configuration.supabasePublishableKey?.takeIf { it.isNotBlank() }
            ?: error("supabase_publishable_key_missing")
        val path = "$profileId/$kind-${browserComposerTimestamp()}-${browserComposerRandomToken()}"
        val result = browserUploadComposerMedia(
            reference = reference,
            uploadUrl = "$baseUrl/storage/v1/object/community-posts/$path",
            apiKey = apiKey,
            accessToken = credentials.accessToken,
        )
        return "$baseUrl/storage/v1/object/public/community-posts/$path${result.extension}"
    }

    private suspend fun createCommunityPost(
        profileId: String,
        wallId: String,
        body: String,
        imageUrl: String?,
        videoUrl: String?,
    ): String {
        val baseUrl = configuration.supabaseUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: error("supabase_url_missing")
        val credentials = authRepository.currentWebPushCredentials() ?: error("web_session_missing")
        val apiKey = configuration.supabasePublishableKey?.takeIf { it.isNotBlank() }
            ?: error("supabase_publishable_key_missing")
        val payload = buildJsonObject {
            put("wall_id", wallId); put("profile_id", profileId); put("author_id", profileId)
            put("body", body); put("content", body)
            imageUrl?.let { put("image_url", it) }
            videoUrl?.let { put("video_url", it) }
        }.toString()
        return browserCreateComposerPost("$baseUrl/rest/v1/community_posts", apiKey, credentials.accessToken, payload)
    }
}

private fun validate(draft: PostComposerDraft) = when (draft.type) {
    PostComposerType.Text -> require(draft.text.isNotBlank()) { "La publicaci\u00f3n de texto no puede estar vac\u00eda" }
    PostComposerType.Image -> require(!draft.imageUri.isNullOrBlank()) { "Selecciona una imagen" }
    PostComposerType.Video -> require(!draft.videoUri.isNullOrBlank()) { "Selecciona o graba un v\u00eddeo" }
}

private fun PostComposerDraft.toRemoteBody(): String = when (type) {
    PostComposerType.Text -> buildPostBodyWithMeta(cleanBody = text, textPattern = textPatternId, channel = "feed")
    PostComposerType.Image -> buildPostBodyWithMeta(imageLocation = locationLabel, channel = "feed")
    PostComposerType.Video -> buildPostBodyWithMeta(mediaTitle = text, channel = "feed")
}

private suspend fun WebPostgrestClient.webComposerRows(
    table: String,
    query: Map<String, String>,
    limit: Int,
) = when (val result = get(table, query, limit)) {
    is WebPostgrestResult.Success -> Json.parseToJsonElement(result.body).jsonArray.map { it.jsonObject }
    is WebPostgrestResult.Failure -> error("web_composer_${result.kind.name.lowercase()}:${result.reason}")
}

private suspend fun browserUploadComposerMedia(reference: String, uploadUrl: String, apiKey: String, accessToken: String): ComposerUploadResult =
    suspendCoroutine { continuation ->
        browserUploadComposerMediaRequest(
            reference,
            uploadUrl,
            apiKey,
            accessToken,
            onSuccess = { extension -> continuation.resume(ComposerUploadResult(extension)) },
            onFailure = { continuation.resumeWith(Result.failure(IllegalStateException(it))) },
        )
    }

private data class ComposerUploadResult(val extension: String)

private fun browserUploadComposerMediaRequest(reference: String, uploadUrl: String, apiKey: String, accessToken: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit): Unit = js(
    """
    (() => {
    if (typeof globalThis.fetch !== 'function') { onFailure('web_composer_fetch_unsupported'); return; }
    globalThis.fetch(reference).then(async (source) => {
      if (!source.ok) throw new Error(`web_composer_media_source_${'$'}{source.status}`);
      const blob = await source.blob();
      const extension = ({'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp', 'video/mp4': '.mp4', 'video/webm': '.webm'})[blob.type] || '';
      const response = await globalThis.fetch(uploadUrl + extension, { method: 'POST', headers: { apikey: apiKey, Authorization: `Bearer ${'$'}{accessToken}`, 'Content-Type': blob.type || 'application/octet-stream', 'x-upsert': 'false' }, body: blob });
      if (!response.ok) throw new Error(`web_composer_media_upload_${'$'}{response.status}`);
      onSuccess(extension);
    }).catch((error) => onFailure(error?.message || error?.name || 'web_composer_media_upload_failed'));
    })()
    """,
)

private suspend fun browserCreateComposerPost(url: String, apiKey: String, accessToken: String, payload: String): String =
    suspendCoroutine { continuation -> browserCreateComposerPostRequest(url, apiKey, accessToken, payload, continuation::resume, { continuation.resumeWith(Result.failure(IllegalStateException(it))) }) }

private fun browserCreateComposerPostRequest(url: String, apiKey: String, accessToken: String, payload: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit): Unit = js(
    """
    (() => {
    if (typeof globalThis.fetch !== 'function') { onFailure('web_composer_fetch_unsupported'); return; }
    globalThis.fetch(url, { method: 'POST', headers: { apikey: apiKey, Authorization: `Bearer ${'$'}{accessToken}`, 'Content-Type': 'application/json', Prefer: 'return=representation' }, body: payload })
      .then(async (response) => { const body = await response.text(); if (response.ok) onSuccess(body); else onFailure(`web_composer_create_${'$'}{response.status}`); })
      .catch((error) => onFailure(error?.message || error?.name || 'web_composer_create_failed'));
    })()
    """,
)

private fun browserComposerTimestamp(): String = js("String(Date.now())")
private fun browserComposerRandomToken(): String = js("Math.random().toString(36).slice(2, 10)")
