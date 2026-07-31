package com.quata.feature.postcomposer.data

import com.quata.core.data.toFoundationData
import com.quata.core.session.IosRenewableAuthSession
import com.quata.feature.postcomposer.domain.PostComposerDraft
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLResponse
import platform.Foundation.NSUUID
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Public, client-safe settings. It deliberately accepts only a publishable Supabase key. */
data class IosPostComposerRuntimeConfiguration(
    val supabaseUrl: String,
    val supabasePublishableKey: String,
    val wordpressBaseUrl: String = "https://egquata.com/",
)

/** URLSession implementation of the same temporary Android-compatible publication path. */
@OptIn(ExperimentalForeignApi::class)
class IosPostComposerTransport(
    private val configuration: IosPostComposerRuntimeConfiguration,
    private val authSession: IosRenewableAuthSession,
) : ActorBoundComposerTransport {
    override suspend fun renewableSession(): ComposerActorSession? = authSession.currentSession()?.let { session ->
        session.userId.trim().takeIf(String::isNotEmpty)?.let { ComposerActorSession(it, "") }
    }

    override suspend fun moderate(actor: ComposerActorSession, draft: PostComposerDraft): Result<Unit> = runCatching {
        val media = draft.imageUri ?: draft.videoUri
        val response = wordpressForm(mapOf(
            "action" to "quqos_moderate_content", "context" to "post", "text" to draft.text,
            "image_name" to (media?.substringAfterLast('/') ?: ""), "image_type" to iosComposerMime(media), "image_score" to "0",
            "display_name" to actor.displayName, "profile_id" to actor.profileId, "url" to "ios://post",
        ))
        val data = Json.parseToJsonElement(response).jsonObject["data"]?.jsonObject
        require(data?.get("action")?.jsonPrimitive?.contentOrNull != "block") {
            data?.get("message")?.jsonPrimitive?.contentOrNull ?: data?.get("reason")?.jsonPrimitive?.contentOrNull ?: "composer_moderation_blocked"
        }
    }

    override suspend fun resolveWallId(actorProfileId: String): Result<String> = runCatching {
        val membership = getRest("community_members?select=wall_id&profile_id=eq.${actorProfileId.iosComposerQuery()}&limit=1")
            .jsonArray.firstOrNull()?.jsonObject?.get("wall_id")?.jsonPrimitive?.contentOrNull
        membership ?: getRest("community_wall_stats?select=id&is_active=eq.true&order=sort_order.asc&limit=1")
            .jsonArray.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull ?: error("composer_wall_unavailable")
    }

    override suspend fun prepareImage(reference: String): Result<ComposerPreparedMedia> = prepare(reference, "imagen.jpg", "image/jpeg")
    override suspend fun prepareVideo(reference: String): Result<ComposerPreparedMedia> = prepare(reference, "video.mp4", "video/mp4")

    private fun prepare(reference: String, fallbackName: String, fallbackMime: String): Result<ComposerPreparedMedia> = runCatching {
        val url = iosComposerLocalUrl(reference) ?: error("ios_composer_local_file_required")
        val name = url.lastPathComponent?.takeIf(String::isNotBlank) ?: fallbackName
        ComposerPreparedMedia(reference, name, iosComposerMime(name).takeIf(String::isNotBlank) ?: fallbackMime)
    }

    override suspend fun uploadImage(actorProfileId: String, media: ComposerPreparedMedia): Result<ComposerUploadedMedia> = runCatching {
        val data = media.localData()
        val ext = media.name.substringAfterLast('.', "jpg").lowercase().filter(Char::isLetterOrDigit).ifBlank { "jpg" }
        val path = "$actorProfileId/img-${iosComposerEpochMillis()}-${NSUUID.UUID().UUIDString.lowercase().replace("-", "").take(7)}.$ext"
        request("${configuration.storageBase()}/object/community-posts/${path.iosComposerPath()}", "POST", data, media.mimeType, upsert = true)
        ComposerUploadedMedia("${configuration.storageBase()}/object/public/community-posts/${path.iosComposerPath()}", "storage:$path")
    }

    override suspend fun uploadVideo(actorProfileId: String, media: ComposerPreparedMedia): Result<ComposerUploadedMedia> = runCatching {
        val boundary = "QuataComposer${NSUUID.UUID().UUIDString}"
        val payload = media.localData().multipartVideo(boundary, media.name, media.mimeType)
        val raw = request("${configuration.wordpressBase()}/wp-json/quqos/v1/upload-video", "POST", payload, "multipart/form-data; boundary=$boundary", authenticated = false)
        val root = Json.parseToJsonElement(raw).jsonObject
        val data = root["data"]?.jsonObject
        val url = root["url"]?.jsonPrimitive?.contentOrNull ?: data?.get("url")?.jsonPrimitive?.contentOrNull ?: error("wordpress_video_url_missing")
        ComposerUploadedMedia(url, url)
    }

    override suspend fun insertPost(request: ComposerPostInsert): Result<String?> = runCatching {
        val body = buildJsonObject { put("wall_id", request.wallId); put("profile_id", request.actorProfileId); put("body", request.body); request.imageUrl?.let { put("image_url", it) }; request.videoUrl?.let { put("video_url", it) } }.toString()
        val result = request("${configuration.restBase()}/community_posts", "POST", body.encodeToByteArray().toFoundationData(), "application/json")
        Json.parseToJsonElement(result).jsonArray.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    override suspend fun rollbackUploadedMedia(media: ComposerUploadedMedia): Result<Unit> = runCatching {
        if (media.rollbackToken.startsWith("storage:")) {
            request("${configuration.storageBase()}/object/community-posts/${media.rollbackToken.removePrefix("storage:").iosComposerPath()}", "DELETE", null, null)
        } else wordpressForm(mapOf("action" to "quqos_delete_post_video", "url" to media.rollbackToken))
        Unit
    }
    override suspend fun releasePreparedMedia(media: ComposerPreparedMedia): Result<Unit> = Result.success(Unit)

    private suspend fun getRest(path: String) = Json.parseToJsonElement(request("${configuration.restBase()}/$path", "GET", null, null))
    private suspend fun wordpressForm(fields: Map<String, String>): String = request("${configuration.wordpressBase()}/wp-admin/admin-ajax.php", "POST", fields.entries.joinToString("&") { "${it.key.iosComposerQuery()}=${it.value.iosComposerQuery()}" }.encodeToByteArray().toFoundationData(), "application/x-www-form-urlencoded", authenticated = false)
    private suspend fun request(url: String, method: String, body: NSData?, contentType: String?, upsert: Boolean = false, authenticated: Boolean = true): String {
        val mutable = NSMutableURLRequest.requestWithURL(NSURL(string = url) ?: error("ios_composer_url_invalid")).apply { setHTTPMethod(method); body?.let(::setHTTPBody); contentType?.let { setValue(it, "Content-Type") }; setValue("application/json", "Accept") }
        if (authenticated) { val session = authSession.currentSession()?.takeIf { it.bearerToken.isNotBlank() } ?: error("ios_composer_session_missing"); mutable.setValue(configuration.key(), "apikey"); mutable.setValue("Bearer ${session.bearerToken}", "Authorization") }
        if (upsert) mutable.setValue("true", "x-upsert")
        return mutable.executeComposer().toIosBytes().decodeToString()
    }
}

@OptIn(ExperimentalForeignApi::class) private fun ComposerPreparedMedia.localData(): NSData { val url = iosComposerLocalUrl(reference) ?: error("ios_composer_local_file_required"); return NSFileManager.defaultManager.contentsAtPath(url.path ?: error("ios_composer_local_path_missing")) ?: error("ios_composer_read_failed") }
@OptIn(ExperimentalForeignApi::class) private fun iosComposerLocalUrl(reference: String): NSURL? = when { reference.startsWith("file://") -> NSURL(string = reference); reference.startsWith("/") -> NSURL.fileURLWithPath(reference); else -> null }?.takeIf { it.isFileURL() }
private fun IosPostComposerRuntimeConfiguration.restBase() = "${supabaseUrl.trimEnd('/')}/rest/v1"
private fun IosPostComposerRuntimeConfiguration.storageBase() = "${supabaseUrl.trimEnd('/')}/storage/v1"
private fun IosPostComposerRuntimeConfiguration.wordpressBase() = wordpressBaseUrl.trimEnd('/')
private fun IosPostComposerRuntimeConfiguration.key() = supabasePublishableKey.trim().takeIf(String::isNotEmpty) ?: error("ios_composer_publishable_key_missing")
private fun String.iosComposerQuery() = encodeToByteArray().joinToString("") { b -> val n=b.toInt() and 255; if(n in 48..57 || n in 65..90 || n in 97..122 || n in intArrayOf(45,46,95,126)) n.toChar().toString() else "%${n.toString(16).padStart(2,'0').uppercase()}" }
private fun String.iosComposerPath() = split('/').joinToString("/") { it.iosComposerQuery() }
private fun iosComposerMime(value: String?) = when(value?.substringBefore('?')?.substringAfterLast('.')?.lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "mp4" -> "video/mp4"; "mov" -> "video/quicktime"; else -> "" }
@OptIn(ExperimentalForeignApi::class)
private fun iosComposerEpochMillis(): Long = platform.posix.time(null) * 1000L
@OptIn(ExperimentalForeignApi::class) private fun NSData.multipartVideo(boundary: String, name: String, mime: String): NSData { val prefix="--$boundary\r\nContent-Disposition: form-data; name=\"video\"; filename=\"$name\"\r\nContent-Type: $mime\r\n\r\n".encodeToByteArray(); val suffix="\r\n--$boundary--\r\n".encodeToByteArray(); return (prefix + toIosBytes() + suffix).toFoundationData() }
private data class IosComposerHttp(val body: NSData)
@OptIn(ExperimentalForeignApi::class) private suspend fun NSMutableURLRequest.executeComposer(): NSData = suspendCancellableCoroutine { c -> val delegate=IosComposerDelegate(c); val session=NSURLSession.sessionWithConfiguration(NSURLSessionConfiguration.ephemeralSessionConfiguration(),delegate,null); val task=session.dataTaskWithRequest(this); c.invokeOnCancellation { task.cancel(); session.invalidateAndCancel() }; task.resume() }
@OptIn(ExperimentalForeignApi::class) private class IosComposerDelegate(private val c:CancellableContinuation<NSData>):NSObject(),NSURLSessionDataDelegateProtocol { private val chunks=mutableListOf<ByteArray>(); override fun URLSession(session:NSURLSession,dataTask:NSURLSessionDataTask,didReceiveData:NSData){if(c.isActive)chunks+=didReceiveData.toIosBytes()}; override fun URLSession(session:NSURLSession,task:NSURLSessionTask,didCompleteWithError:platform.Foundation.NSError?){session.finishTasksAndInvalidate();if(!c.isActive)return;if(didCompleteWithError!=null)c.resumeWithException(IllegalStateException(didCompleteWithError.localizedDescription));else { val status=(task.response as? NSHTTPURLResponse)?.statusCode?.toInt();if(status==null||status !in 200..299)c.resumeWithException(IllegalStateException("ios_composer_http_${status?:"unknown"}"));else c.resume(chunks.fold(ByteArray(0)){a,b->a+b}.toFoundationData()) } } }
@OptIn(ExperimentalForeignApi::class) private fun NSData.toIosBytes():ByteArray=if(length==0uL)ByteArray(0)else bytes?.readBytes(length.toInt())?:ByteArray(0)
