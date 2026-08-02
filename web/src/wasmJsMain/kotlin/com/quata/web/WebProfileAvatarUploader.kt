@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.quata.web

import com.quata.core.platform.BrowserCameraCaptureService
import com.quata.core.platform.FilePickerReferenceReleaser
import com.quata.core.platform.PlatformFile
import com.quata.feature.profile.data.ProfileAvatarUploader
import com.quata.feature.postcomposer.imageeditor.AvatarImageEditorTransform
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
    fun editorTransform(reference: String): AvatarImageEditorTransform = AvatarImageEditorTransform.Default
    fun saveEditorTransform(reference: String, transform: AvatarImageEditorTransform) = Unit
}

internal class WebProfileAvatarReferenceRegistry(
    private val galleryReleaser: FilePickerReferenceReleaser,
    private val camera: BrowserCameraCaptureService,
) : WebProfileAvatarReferenceStore {
    private val releasers = mutableMapOf<String, suspend () -> Unit>()
    private val transforms = mutableMapOf<String, AvatarImageEditorTransform>()

    fun ownGallery(file: PlatformFile) {
        releasers[file.reference] = { galleryReleaser.release(file) }
        transforms[file.reference] = AvatarImageEditorTransform.Default
    }

    fun ownCamera(file: PlatformFile) {
        releasers[file.reference] = { camera.release(file) }
        transforms[file.reference] = AvatarImageEditorTransform.Default
    }

    override suspend fun release(reference: String?) {
        reference?.let { transforms.remove(it); releasers.remove(it) }?.invoke()
    }

    override fun editorTransform(reference: String): AvatarImageEditorTransform =
        transforms[reference] ?: AvatarImageEditorTransform.Default

    override fun saveEditorTransform(reference: String, transform: AvatarImageEditorTransform) {
        if (reference in releasers) transforms[reference] = transform
    }
}

internal data class WebProfileAvatarPreparedImage(
    val reference: String,
    val mimeType: String = "image/jpeg",
)

/** Geometry shared by the browser export and its unit tests.  Rotation changes the output axes. */
internal data class WebProfileAvatarExportGeometry(
    val scale: Float,
    val outputDrawnWidth: Float,
    val outputDrawnHeight: Float,
    val maxPanX: Float,
    val maxPanY: Float,
)

internal fun webProfileAvatarExportGeometry(
    sourceWidth: Int,
    sourceHeight: Int,
    transform: AvatarImageEditorTransform,
    outputSide: Int = 1080,
): WebProfileAvatarExportGeometry {
    require(sourceWidth > 0 && sourceHeight > 0)
    val scale = maxOf(outputSide.toFloat() / sourceWidth, outputSide.toFloat() / sourceHeight) * transform.zoom
    val sourceDrawnWidth = sourceWidth * scale
    val sourceDrawnHeight = sourceHeight * scale
    val isQuarterTurn = transform.quarterTurns % 2 != 0
    val outputDrawnWidth = if (isQuarterTurn) sourceDrawnHeight else sourceDrawnWidth
    val outputDrawnHeight = if (isQuarterTurn) sourceDrawnWidth else sourceDrawnHeight
    return WebProfileAvatarExportGeometry(
        scale = scale,
        outputDrawnWidth = outputDrawnWidth,
        outputDrawnHeight = outputDrawnHeight,
        maxPanX = ((outputDrawnWidth - outputSide) / 2f).coerceAtLeast(0f),
        maxPanY = ((outputDrawnHeight - outputSide) / 2f).coerceAtLeast(0f),
    )
}

/** Injectable edge so path/header and cancellation guarantees are testable without a browser DOM. */
internal interface WebProfileAvatarBinaryTransport {
    suspend fun prepareSquareJpeg(reference: String, transform: AvatarImageEditorTransform): WebProfileAvatarPreparedImage
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
    /** The authenticated session carries both credentials and the authoritative actor id. */
    private val sessionForAuthenticatedRequest: suspend () -> WebLocalSession?,
    private val references: WebProfileAvatarReferenceStore,
    private val binary: WebProfileAvatarBinaryTransport = BrowserWebProfileAvatarBinaryTransport,
    private val token: () -> String = ::webComposerRandomToken,
) : ProfileAvatarUploader {
    constructor(
        configuration: WebRuntimeConfiguration,
        auth: WebAuthRepository,
        references: WebProfileAvatarReferenceStore,
    ) : this(configuration, auth::sessionForAuthenticatedRequest, references)

    override suspend fun uploadIfNeeded(profileId: String, avatarUri: String?): String? {
        val normalized = avatarUri?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (isBrowserAvatarUrl(normalized)) return normalized
        require(isBrowserAvatarBlobUrl(normalized)) { "web_profile_avatar_reference_invalid" }
        var prepared: WebProfileAvatarPreparedImage? = null
        try {
            val session = sessionForAuthenticatedRequest() ?: error("web_session_missing")
            check(session.userId == profileId) { "web_profile_avatar_actor_mismatch" }
            val baseUrl = configuration.supabaseUrl?.trimEnd('/')?.takeIf(String::isNotBlank)
                ?: error("supabase_url_missing")
            val key = configuration.supabasePublishableKey?.takeIf(String::isNotBlank)
                ?: error("supabase_publishable_key_missing")
            prepared = binary.prepareSquareJpeg(normalized, references.editorTransform(normalized))
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
    override suspend fun prepareSquareJpeg(reference: String, transform: AvatarImageEditorTransform): WebProfileAvatarPreparedImage =
        webProfilePrepareSquareAvatar(reference, transform)

    override suspend fun upload(reference: String, url: String, key: String, token: String, mimeType: String) {
        webComposerUploadBlob(reference, url, key, token, mimeType)
    }

    override fun revokePrepared(reference: String) = webProfileRevokeBlobUrl(reference)
}

private suspend fun webProfilePrepareSquareAvatar(reference: String, transform: AvatarImageEditorTransform): WebProfileAvatarPreparedImage = suspendCoroutine { continuation ->
    webProfilePrepareSquareAvatarJs(
        reference = reference,
        zoom = transform.zoom,
        panX = transform.panX,
        panY = transform.panY,
        quarterTurns = transform.quarterTurns,
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
    zoom: Float,
    panX: Float,
    panY: Float,
    quarterTurns: Int,
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
          const canvas = globalThis.document.createElement('canvas');
          // Android's avatar output contract is always a 1080x1080 JPEG, including
          // upscaling smaller source images after a centered square crop.
          canvas.width = 1080; canvas.height = 1080;
          const context = canvas.getContext('2d');
          if (!context) throw Error('web_profile_avatar_canvas_context_unavailable');
          const turns = ((Number(quarterTurns) % 4) + 4) % 4;
          const scale = Math.max(1080 / width, 1080 / height) * Math.min(4, Math.max(1, Number(zoom) || 1));
          const sourceDrawnWidth = width * scale;
          const sourceDrawnHeight = height * scale;
          // context.rotate swaps the visible axes at 90°/270°.  Pan is expressed in
          // output-canvas axes, so its overflow must swap too or a portrait image can expose a
          // transparent stripe on one side while the preview appears correctly constrained.
          const outputDrawnWidth = turns % 2 === 0 ? sourceDrawnWidth : sourceDrawnHeight;
          const outputDrawnHeight = turns % 2 === 0 ? sourceDrawnHeight : sourceDrawnWidth;
          const maxPanX = Math.max(0, (outputDrawnWidth - 1080) / 2);
          const maxPanY = Math.max(0, (outputDrawnHeight - 1080) / 2);
          context.save();
          context.translate(540 + Math.max(-1, Math.min(1, Number(panX) || 0)) * maxPanX, 540 + Math.max(-1, Math.min(1, Number(panY) || 0)) * maxPanY);
          context.rotate(turns * Math.PI / 2);
          context.drawImage(image, -sourceDrawnWidth / 2, -sourceDrawnHeight / 2, sourceDrawnWidth, sourceDrawnHeight);
          context.restore();
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
