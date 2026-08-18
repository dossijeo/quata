@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.feature.postcomposer.data.ActorBoundComposerTransport
import com.quata.feature.postcomposer.data.ComposerActorSession
import com.quata.feature.postcomposer.data.ComposerPostInsert
import com.quata.feature.postcomposer.data.ComposerPreparedMedia
import com.quata.feature.postcomposer.data.ComposerUploadedMedia
import com.quata.feature.postcomposer.data.composerModerationFields
import com.quata.feature.postcomposer.domain.PostComposerDestination
import com.quata.feature.postcomposer.domain.PostComposerDraft
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Real browser adapter matching Android's temporary migration contract (no synthetic IDs). */
class WebPostComposerTransport(
    private val configuration: WebRuntimeConfiguration,
    private val auth: WebAuthRepository,
    private val postgrest: WebPostgrestClient = WebPostgrestClient(configuration, auth),
) : ActorBoundComposerTransport {
    override suspend fun renewableSession(): ComposerActorSession? = auth.sessionForAuthenticatedRequest()?.let { session ->
        val displayName = session.displayName?.trim()?.takeIf(String::isNotBlank)
            ?: resolveComposerDisplayName(session.userId)
        ComposerActorSession(session.userId, displayName)
    }

    private suspend fun resolveComposerDisplayName(profileId: String): String =
        postgrest.get(WEB_COMPOSER_PROFILES_TABLE, webComposerProfileDisplayNameQuery(profileId))
            .webComposerBody()
            .let { Json.parseToJsonElement(it).jsonArray.singleOrNull()?.jsonObject?.get("display_name")?.jsonPrimitive?.contentOrNull }
            ?.trim()?.takeIf(String::isNotBlank) ?: error("composer_actor_display_name_missing")

    override suspend fun moderate(actor: ComposerActorSession, draft: PostComposerDraft): Result<Unit> = runCatching {
        val media = when { draft.imageUri != null -> draft.imageUri; draft.videoUri != null -> draft.videoUri; else -> null }
        val json = webComposerWordpressForm(
            url = webComposerWordpressUrl(configuration, "wp-admin/admin-ajax.php"),
            fields = composerModerationFields(actor, draft, media?.substringAfterLast('/') ?: "", mediaMime(media), "web://post"),
        )
        val root = Json.parseToJsonElement(json).jsonObject
        val data = root["data"]?.jsonObject
        require(data?.get("action")?.jsonPrimitive?.contentOrNull != "block") {
            data?.get("message")?.jsonPrimitive?.contentOrNull ?: data?.get("reason")?.jsonPrimitive?.contentOrNull ?: "composer_moderation_blocked"
        }
    }

    override suspend fun resolveWallId(actorProfileId: String): Result<String> = runCatching {
        val membership = postgrest.get("community_members", mapOf("select" to "wall_id", "profile_id" to "eq.$actorProfileId", "limit" to "1"))
            .webComposerBody().let { Json.parseToJsonElement(it).jsonArray.firstOrNull()?.jsonObject?.get("wall_id")?.jsonPrimitive?.contentOrNull }
        membership ?: postgrest.get(WEB_COMPOSER_WALL_STATS_TABLE, webComposerWallFallbackQuery())
            .webComposerBody().let { Json.parseToJsonElement(it).jsonArray.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull }
            ?: error("composer_wall_unavailable")
    }

    override suspend fun loadDestinations(actorProfileId: String): Result<List<PostComposerDestination>> = runCatching {
        val memberWallIds = postgrest.get("community_members", mapOf("select" to "wall_id", "profile_id" to "eq.$actorProfileId"))
            .webComposerBody()
            .let { Json.parseToJsonElement(it).jsonArray }
            .mapNotNull { it.jsonObject["wall_id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }
            .distinct()
        val walls = postgrest.get(WEB_COMPOSER_WALL_STATS_TABLE, webComposerDestinationQuery())
            .webComposerBody()
            .let { Json.parseToJsonElement(it).jsonArray }
            .mapNotNull { row ->
                val obj = row.jsonObject
                val wallId = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val label = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: obj["slug"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: "Feed"
                val subtitle = obj["city"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: obj["description"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                PostComposerDestination(wallId, label, subtitle, isDefault = false)
            }
        val fallbackId = memberWallIds.firstOrNull() ?: walls.firstOrNull()?.wallId
        val memberSet = memberWallIds.toSet()
        walls
            .filter { it.wallId in memberSet || memberSet.isEmpty() }
            .map { it.copy(isDefault = it.wallId == fallbackId) }
            .ifEmpty { fallbackId?.let { listOf(PostComposerDestination(it, "Feed", isDefault = true)) }.orEmpty() }
    }

    override suspend fun prepareImage(reference: String): Result<ComposerPreparedMedia> = Result.success(webPrepared(reference, "image/jpeg", "imagen.jpg"))
    override suspend fun prepareVideo(reference: String): Result<ComposerPreparedMedia> = Result.success(webPrepared(reference, "video/mp4", "video.mp4"))

    override suspend fun uploadImage(actorProfileId: String, media: ComposerPreparedMedia): Result<ComposerUploadedMedia> = runCatching {
        val credentials = auth.currentWebPushCredentials() ?: error("web_session_missing")
        val base = configuration.supabaseUrl?.trimEnd('/') ?: error("supabase_url_missing")
        val key = configuration.supabasePublishableKey?.takeIf(String::isNotBlank) ?: error("supabase_publishable_key_missing")
        val ext = media.name.substringAfterLast('.', "jpg").lowercase().filter(Char::isLetterOrDigit).ifBlank { "jpg" }
        val path = "$actorProfileId/img-${webComposerTimestamp()}-${webComposerRandomToken()}.$ext"
        webComposerUploadBlob(media.reference, webComposerStorageObjectUrl(base, path), key, credentials.accessToken, media.mimeType)
        ComposerUploadedMedia(webComposerStoragePublicUrl(base, path), "storage:$path")
    }

    override suspend fun uploadVideo(actorProfileId: String, media: ComposerPreparedMedia): Result<ComposerUploadedMedia> = runCatching {
        val json = webComposerMultipartVideo(webComposerWordpressUrl(configuration, "wp-json/quqos/v1/upload-video"), media.reference, media.name, media.mimeType)
        val root = Json.parseToJsonElement(json).jsonObject
        val data = root["data"]?.jsonObject
        val url = root["url"]?.jsonPrimitive?.contentOrNull ?: data?.get("url")?.jsonPrimitive?.contentOrNull ?: error("wordpress_video_url_missing")
        ComposerUploadedMedia(url, url)
    }

    override suspend fun insertPost(request: ComposerPostInsert): Result<String?> = runCatching {
        val body = webComposerPostBody(request)
        val response = postgrest.post(WEB_COMPOSER_POSTS_TABLE, body).webComposerBody()
        Json.parseToJsonElement(response).jsonArray.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    override suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia): Result<Unit> = runCatching {
        if (media.rollbackToken.startsWith("storage:")) {
            val credentials = auth.currentWebPushCredentials() ?: error("web_session_missing")
            val base = configuration.supabaseUrl?.trimEnd('/') ?: error("supabase_url_missing")
            val key = configuration.supabasePublishableKey?.takeIf(String::isNotBlank) ?: error("supabase_publishable_key_missing")
            webComposerDeleteStorageBlob(
                webComposerStorageObjectUrl(base, media.rollbackToken.removePrefix("storage:")),
                key,
                credentials.accessToken,
            )
        } else {
            webComposerWordpressForm(webComposerWordpressUrl(configuration, "wp-admin/admin-ajax.php"), webComposerVideoDeleteFields(media.rollbackToken))
        }
    }
    override suspend fun releasePreparedMedia(media: ComposerPreparedMedia): Result<Unit> = Result.success(Unit)
}

private fun WebPostgrestResult.webComposerBody(): String = when (this) { is WebPostgrestResult.Success -> body; is WebPostgrestResult.Failure -> error(reason) }
internal fun webPrepared(reference: String, fallbackMime: String, fallbackName: String): ComposerPreparedMedia {
    val rawName = reference.substringAfterLast('/').substringBefore('?').trim()
    val name = rawName.takeIf { "." in it } ?: fallbackName
    return ComposerPreparedMedia(reference, name, mediaMime(name).takeIf(String::isNotBlank) ?: fallbackMime)
}
private fun mediaMime(reference: String?): String = when (reference?.substringBefore('?')?.substringAfterLast('.')?.lowercase()) { "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"; "mov" -> "video/quicktime"; "mp4" -> "video/mp4"; else -> "" }
internal const val WEB_COMPOSER_WALL_STATS_TABLE = "community_walls_stats"
internal const val WEB_COMPOSER_POSTS_TABLE = "community_posts"
internal const val WEB_COMPOSER_PROFILES_TABLE = "community_profiles"
internal fun webComposerWallFallbackQuery(): Map<String, String> = mapOf("select" to "id", "is_active" to "eq.true", "order" to "sort_order.asc", "limit" to "1")
internal fun webComposerDestinationQuery(): Map<String, String> = mapOf("select" to "id,slug,name,city,description", "is_active" to "eq.true", "order" to "sort_order.asc,chat_last_at.desc,created_at.desc", "limit" to "250")
internal fun webComposerProfileDisplayNameQuery(profileId: String) = mapOf("select" to "display_name", "id" to "eq.$profileId", "limit" to "1")
internal fun webComposerStorageObjectUrl(base: String, path: String) = "${base.trimEnd('/')}/storage/v1/object/community-posts/${webEncodePath(path)}"
internal fun webComposerStoragePublicUrl(base: String, path: String) = "${base.trimEnd('/')}/storage/v1/object/public/community-posts/${webEncodePath(path)}"
internal fun webComposerVideoDeleteFields(url: String) = mapOf("action" to "quqos_delete_post_video", "url" to url)
internal fun webComposerPostBody(request: ComposerPostInsert) = buildJsonObject {
    put("wall_id", request.wallId)
    put("profile_id", request.actorProfileId)
    put("body", request.body)
    request.imageUrl?.let { put("image_url", it) }
    request.videoUrl?.let { put("video_url", it) }
}.toString()
internal fun webComposerWordpressUrl(configuration: WebRuntimeConfiguration, path: String): String =
    webComposerWordpressUrl(configuration, path, webComposerIsLocalhost())
internal fun webComposerWordpressUrl(configuration: WebRuntimeConfiguration, path: String, isLocalhost: Boolean): String =
    if (isLocalhost) "/wordpress-proxy/$path" else "${configuration.wordpressBaseUrl.trimEnd('/')}/$path"
private fun webComposerIsLocalhost(): Boolean = js("globalThis.location?.hostname === 'localhost' || globalThis.location?.hostname === '127.0.0.1'")
private fun webEncodePath(path: String): String = js("path.split('/').map(encodeURIComponent).join('/')")
private fun webComposerTimestamp(): String = js("String(Date.now())")
internal fun webComposerRandomToken(): String = js("Math.random().toString(36).slice(2,9)")

internal data class WebComposerHttpContract(val method: String, val url: String, val headers: Map<String, String>)
internal fun webComposerStorageUploadContract(url: String, key: String, token: String, mime: String) = WebComposerHttpContract(
    method = "POST", url = url,
    headers = mapOf("apikey" to key, "Authorization" to "Bearer $token", "Content-Type" to mime, "x-upsert" to "true"),
)
internal fun webComposerStorageDeleteContract(url: String, key: String, token: String) = WebComposerHttpContract(
    method = "DELETE", url = url, headers = mapOf("apikey" to key, "Authorization" to "Bearer $token"),
)

private suspend fun webComposerWordpressForm(url: String, fields: Map<String, String>): String = webComposerRequest("form", url, fields, null, null, null)
private suspend fun webComposerMultipartVideo(url: String, reference: String, name: String, mime: String): String = webComposerRequest("video", url, emptyMap(), reference, name, mime)
/** Shared authenticated Storage binary upload used by Composer and Profile avatars. */
internal suspend fun webComposerUploadBlob(reference: String, url: String, key: String, token: String, mime: String) {
    val contract = webComposerStorageUploadContract(url, key, token, mime)
    webComposerRequest("storage", contract.url, contract.headers, reference, null, mime)
}
private suspend fun webComposerDeleteStorageBlob(url: String, key: String, token: String) {
    val contract = webComposerStorageDeleteContract(url, key, token)
    webComposerRequest("storage-delete", contract.url, contract.headers, null, null, null)
}
private suspend fun webComposerRequest(kind: String, url: String, fields: Map<String, String>, reference: String?, name: String?, mime: String?): String = suspendCoroutine { c ->
    val encoded = buildJsonObject { fields.forEach { (key, value) -> put(key, value) } }.toString()
    webComposerFetch(kind, url, encoded, reference, name, mime, { c.resume(it) }, { c.resumeWith(Result.failure(IllegalStateException(it))) })
}
private fun webComposerFetch(kind: String, url: String, fields: String, reference: String?, name: String?, mime: String?, ok: (String) -> Unit, fail: (String) -> Unit): Unit = js("""(() => { const f=JSON.parse(fields); const source=reference ? fetch(reference).then(r=>{if(!r.ok)throw Error('composer_media_source_'+r.status);return r.blob()}) : Promise.resolve(null); source.then(blob=>{let o={method:'POST',headers:{Accept:'application/json'}}; if(kind==='form'){o.body=new URLSearchParams(f);o.headers['X-Requested-With']='XMLHttpRequest'} else if(kind==='video'){let d=new FormData();let typed=blob&&mime?new Blob([blob],{type:mime}):blob;d.append('video',typed,name||'video.mp4');o.body=d} else if(kind==='storage-delete'){o.method='DELETE';o.headers=f} else {o.headers=f;o.body=blob} return fetch(url,o)}).then(async r=>{let t=await r.text();if(!r.ok)throw Error('composer_http_'+r.status+':'+t);ok(t)}).catch(e=>fail(e?.message||'composer_request_failed')) })()""")
