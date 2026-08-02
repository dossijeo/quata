package com.quata.feature.profile.data

import com.quata.core.data.toFoundationData
import com.quata.core.session.IosRenewableAuthSession
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import kotlin.math.max

internal data class IosProfileAvatarUploadRequest(
    val url: String,
    val publicUrl: String,
    val headers: Map<String, String>,
    val body: NSData,
)

internal fun interface IosProfileAvatarBinaryTransport {
    suspend fun upload(request: IosProfileAvatarUploadRequest)
}

internal fun interface IosProfileAvatarEncoder {
    fun encode(reference: String): NSData
}

@OptIn(ExperimentalForeignApi::class)
private object IosUrlSessionProfileAvatarTransport : IosProfileAvatarBinaryTransport {
    override suspend fun upload(request: IosProfileAvatarUploadRequest) {
        val url = NSURL(string = request.url) ?: error("ios_profile_avatar_url_invalid")
        val native = platform.Foundation.NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod("POST")
            request.headers.forEach { (name, value) -> setValue(value, name) }
            setHTTPBody(request.body)
        }
        platform.Foundation.NSURLSessionConfiguration.ephemeralSessionConfiguration().iosProfileData(native)
    }
}

@OptIn(ExperimentalForeignApi::class)
private object IosSquareProfileAvatarEncoder : IosProfileAvatarEncoder {
    override fun encode(reference: String): NSData {
        val url = NSURL(string = reference)?.takeIf { it.isFileURL() }
            ?: error("ios_profile_avatar_local_file_required")
        val source = NSData.dataWithContentsOfURL(url) ?: error("ios_profile_avatar_read_failed")
        val image = UIImage.imageWithData(source) ?: error("ios_profile_avatar_decode_failed")
        val width = image.size.useContents { width }
        val height = image.size.useContents { height }
        require(width > 0.0 && height > 0.0) { "ios_profile_avatar_dimensions_invalid" }
        val side = 900.0
        val scale = max(side / width, side / height)
        val drawWidth = width * scale
        val drawHeight = height * scale
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(side, side), false, 1.0)
        image.drawInRect(CGRectMake((side - drawWidth) / 2.0, (side - drawHeight) / 2.0, drawWidth, drawHeight))
        val cropped = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return cropped?.let { UIImageJPEGRepresentation(it, 0.82) }
            ?: error("ios_profile_avatar_encode_failed")
    }
}

class IosProfileAvatarUploader internal constructor(
    private val configuration: IosProfileRuntimeConfiguration,
    private val sessionProvider: IosProfileSessionProvider,
    private val transport: IosProfileAvatarBinaryTransport,
    private val encoder: IosProfileAvatarEncoder,
    private val token: () -> String,
) : ProfileAvatarUploader {
    constructor(
        configuration: IosProfileRuntimeConfiguration,
        authSession: IosRenewableAuthSession,
    ) : this(
        configuration,
        IosProfileKeychainSessionProvider(authSession),
        IosUrlSessionProfileAvatarTransport,
        IosSquareProfileAvatarEncoder,
        { NSUUID.UUID().UUIDString.replace("-", "").take(20) },
    )

    override suspend fun uploadIfNeeded(profileId: String, avatarUri: String?): String? {
        val normalized = avatarUri?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (normalized.startsWith("https://") || normalized.startsWith("http://")) return normalized
        val session = sessionProvider.currentSession() ?: error("ios_profile_session_missing")
        requireIosProfileAvatarActor(profileId, session.profileId)
        val body = encoder.encode(normalized)
        val request = iosProfileAvatarUploadRequest(configuration, session.accessToken, profileId, token(), body)
        transport.upload(request)
        return request.publicUrl
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosProfileAvatarUploadRequest(
    configuration: IosProfileRuntimeConfiguration,
    accessToken: String,
    profileId: String,
    token: String,
    body: NSData,
): IosProfileAvatarUploadRequest {
    require(accessToken.isNotBlank()) { "ios_profile_session_missing" }
    requireIosProfileAvatarActor(profileId, profileId)
    require(token.matches(Regex("[A-Za-z0-9_-]+"))) { "ios_profile_avatar_token_invalid" }
    val base = configuration.supabaseUrl.trim().trimEnd('/').ifBlank { error("ios_profile_supabase_url_missing") }
    val key = configuration.supabasePublishableKey.trim().ifBlank { error("ios_profile_supabase_publishable_key_missing") }
    val path = "avatars/$profileId/$token.jpg"
    return IosProfileAvatarUploadRequest(
        url = "$base/storage/v1/object/community-posts/$path",
        publicUrl = "$base/storage/v1/object/public/community-posts/$path",
        headers = mapOf(
            "apikey" to key,
            "Authorization" to "Bearer $accessToken",
            "Content-Type" to "image/jpeg",
            "x-upsert" to "true",
        ),
        body = body,
    )
}

internal fun requireIosProfileAvatarActor(profileId: String, sessionProfileId: String) {
    require(profileId.matches(Regex("[A-Za-z0-9_-]+"))) { "ios_profile_avatar_profile_invalid" }
    check(profileId == sessionProfileId) { "ios_profile_avatar_actor_mismatch" }
}
