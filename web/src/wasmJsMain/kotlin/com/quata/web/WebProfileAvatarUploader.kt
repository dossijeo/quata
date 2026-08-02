@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.platform.BrowserCameraCaptureService
import com.quata.core.platform.FilePickerReferenceReleaser
import com.quata.core.platform.PlatformFile
import com.quata.feature.profile.data.ProfileAvatarUploader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Owns only the temporary Blob URLs selected by Cuenta.  The common ViewModel stores a reference
 * until Save; this registry guarantees it is released after upload, failure or replacement.
 */
internal interface WebProfileAvatarReferenceStore {
    suspend fun release(reference: String?)
}

internal class WebProfileAvatarReferenceRegistry(
    private val galleryReleaser: FilePickerReferenceReleaser,
    private val camera: BrowserCameraCaptureService,
) : WebProfileAvatarReferenceStore {
    private val releasers = mutableMapOf<String, suspend () -> Unit>()

    fun ownGallery(file: PlatformFile) {
        releasers[file.reference] = { galleryReleaser.release(file) }
    }

    fun ownCamera(file: PlatformFile) {
        releasers[file.reference] = { camera.release(file) }
    }

    override suspend fun release(reference: String?) {
        reference?.let { releasers.remove(it) }?.invoke()
    }
}

internal data class WebProfileAvatarPreparedImage(
    val reference: String,
    val mimeType: String = "image/jpeg",
)

/** Injectable edge so path/header and cancellation guarantees are testable without a browser DOM. */
internal interface WebProfileAvatarBinaryTransport {
    suspend fun prepareSquareJpeg(reference: String): WebProfileAvatarPreparedImage
    suspend fun upload(reference: String, url: String, key: String, token: String, mimeType: String)
    fun revokePrepared(reference: String)
}

internal data class WebProfileAvatarUploadPlan(
    val path: String,
    val publicUrl: String,
    val request: WebComposerHttpContract,
)

internal fun webProfileAvatarUploadPlan(
    baseUrl: String,
    publishableKey: String,
    accessToken: String,
    profileId: String,
    token: String,
): WebProfileAvatarUploadPlan {
    require(profileId.matches(WebProfileAvatarProfileId)) { "web_profile_avatar_actor_invalid" }
    val safeToken = token.filter(Char::isLetterOrDigit).take(40)
    require(safeToken.isNotBlank()) { "web_profile_avatar_token_invalid" }
    val path = "avatars/$profileId/$safeToken.jpg"
    val objectUrl = webComposerStorageObjectUrl(baseUrl, path)
    return WebProfileAvatarUploadPlan(
        path = path,
        publicUrl = webComposerStoragePublicUrl(baseUrl, path),
        request = webComposerStorageUploadContract(objectUrl, publishableKey, accessToken, "image/jpeg"),
    )
}

/**
 * Browser Profile avatar implementation. Existing remote avatars pass through; a new Blob is
 * cropped to a square JPEG before its authenticated Storage write. The Blob is never persisted
 * in the profile record and is always released after this method returns.
 */
internal class WebProfileAvatarUploader(
    private val configuration: WebRuntimeConfiguration,
    private val credentials: suspend () -> WebPushCredentials?,
    private val references: WebProfileAvatarReferenceStore,
    private val binary: WebProfileAvatarBinaryTransport = BrowserWebProfileAvatarBinaryTransport,
    private val token: () -> String = ::webComposerRandomToken,
) : ProfileAvatarUploader {
    constructor(
        configuration: WebRuntimeConfiguration,
        auth: WebAuthRepository,
        references: WebProfileAvatarReferenceStore,
    ) : this(configuration, auth::currentWebPushCredentials, references)

    override suspend fun uploadIfNeeded(profileId: String, avatarUri: String?): String? {
        val normalized = avatarUri?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (isBrowserAvatarUrl(normalized)) return normalized
        require(isBrowserAvatarBlobUrl(normalized)) { "web_profile_avatar_reference_invalid" }
        var prepared: WebProfileAvatarPreparedImage? = null
        try {
            val session = credentials() ?: error("web_session_missing")
            val baseUrl = configuration.supabaseUrl?.trimEnd('/')?.takeIf(String::isNotBlank)
                ?: error("supabase_url_missing")
            val key = configuration.supabasePublishableKey?.takeIf(String::isNotBlank)
                ?: error("supabase_publishable_key_missing")
            prepared = binary.prepareSquareJpeg(normalized)
            val plan = webProfileAvatarUploadPlan(baseUrl, key, session.accessToken, profileId, token())
            binary.upload(prepared.reference, plan.request.url, key, session.accessToken, prepared.mimeType)
            return plan.publicUrl
        } finally {
            prepared?.let { binary.revokePrepared(it.reference) }
            references.release(normalized)
        }
    }
}

/** Existing persisted server values remain valid; transient Blob URLs must take the uploader path. */
internal fun webProfileAvatarUploadReference(avatarUri: String?): String? {
    val normalized = avatarUri?.trim()?.takeIf(String::isNotBlank) ?: return null
    if (isBrowserAvatarUrl(normalized)) return normalized
    throw UnsupportedOperationException("web_profile_avatar_upload_not_available")
}

private object BrowserWebProfileAvatarBinaryTransport : WebProfileAvatarBinaryTransport {
    override suspend fun prepareSquareJpeg(reference: String): WebProfileAvatarPreparedImage =
        webProfilePrepareSquareAvatar(reference)

    override suspend fun upload(reference: String, url: String, key: String, token: String, mimeType: String) {
        webComposerUploadBlob(reference, url, key, token, mimeType)
    }

    override fun revokePrepared(reference: String) = webProfileRevokeBlobUrl(reference)
}

private suspend fun webProfilePrepareSquareAvatar(reference: String): WebProfileAvatarPreparedImage = suspendCoroutine { continuation ->
    webProfilePrepareSquareAvatarJs(
        reference = reference,
        onSuccess = { payload ->
            runCatching {
                val root = Json.parseToJsonElement(payload).jsonObject
                WebProfileAvatarPreparedImage(
                    reference = root["reference"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf(::isBrowserAvatarBlobUrl) ?: error("web_profile_avatar_processed_reference_invalid"),
                    mimeType = root["mimeType"]?.jsonPrimitive?.contentOrNull ?: "image/jpeg",
                )
            }.fold(continuation::resume, { continuation.resumeWith(Result.failure(it)) })
        },
        onFailure = { continuation.resumeWith(Result.failure(IllegalStateException(it))) },
    )
}

private fun webProfilePrepareSquareAvatarJs(
    reference: String,
    onSuccess: (String) -> Unit,
    onFailure: (String) -> Unit,
): Unit = js(
    """
    (() => {
      try {
        if (typeof fetch !== 'function' || !globalThis.document?.createElement || !globalThis.URL?.createObjectURL) {
          onFailure('web_profile_avatar_canvas_unsupported'); return;
        }
        fetch(reference).then(response => {
          if (!response.ok) throw Error('web_profile_avatar_source_' + response.status);
          return response.blob();
        }).then(source => new Promise((resolve, reject) => {
          const image = new Image();
          const sourceUrl = globalThis.URL.createObjectURL(source);
          image.onload = () => { globalThis.URL.revokeObjectURL(sourceUrl); resolve(image); };
          image.onerror = () => { globalThis.URL.revokeObjectURL(sourceUrl); reject(Error('web_profile_avatar_decode_failed')); };
          image.src = sourceUrl;
        })).then(image => {
          const width = Number(image.naturalWidth || image.width || 0);
          const height = Number(image.naturalHeight || image.height || 0);
          if (!width || !height) throw Error('web_profile_avatar_dimensions_invalid');
          const sourceSide = Math.min(width, height);
          const side = Math.min(1080, sourceSide);
          const sourceX = Math.floor((width - sourceSide) / 2);
          const sourceY = Math.floor((height - sourceSide) / 2);
          const canvas = globalThis.document.createElement('canvas');
          canvas.width = side; canvas.height = side;
          const context = canvas.getContext('2d');
          if (!context) throw Error('web_profile_avatar_canvas_context_unavailable');
          context.drawImage(image, sourceX, sourceY, sourceSide, sourceSide, 0, 0, side, side);
          canvas.toBlob(blob => {
            if (!blob || !blob.size) { onFailure('web_profile_avatar_encode_failed'); return; }
            onSuccess(JSON.stringify({ reference: globalThis.URL.createObjectURL(blob), mimeType: 'image/jpeg' }));
          }, 'image/jpeg', 0.9);
        }).catch(error => onFailure(error?.message || 'web_profile_avatar_prepare_failed'));
      } catch (error) { onFailure(error?.message || 'web_profile_avatar_prepare_failed'); }
    })()
    """,
)

private fun webProfileRevokeBlobUrl(reference: String): Unit = js(
    """(() => { if (typeof reference === 'string' && reference.startsWith('blob:')) globalThis.URL?.revokeObjectURL?.(reference); })()""",
)

private val WebProfileAvatarProfileId = Regex("[A-Za-z0-9_-]{1,128}")
