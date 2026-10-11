package com.quata.feature.chat.data

/** Stable Storage identity for a Chat attachment. Signed URLs are derived and never persisted. */
data class ChatAttachmentStorageObject(
    val bucket: String,
    val path: String,
)

/** Platform boundary used to exchange a stable object identity for a short-lived read URL. */
fun interface ChatAttachmentUrlSigner {
    suspend fun sign(storageObject: ChatAttachmentStorageObject): String
}

object ChatAttachmentStorageAccessPolicy {
    const val SignedUrlLifetimeSeconds = 300

    fun storageObjectOrNull(bucket: String?, path: String?): ChatAttachmentStorageObject? {
        val canonicalBucket = bucket?.takeIf { it in ChatAttachmentPublicUrlPolicy.AllowedBuckets } ?: return null
        val canonicalPath = path?.trim()?.takeIf(::isCanonicalObjectPath) ?: return null
        return ChatAttachmentStorageObject(bucket = canonicalBucket, path = canonicalPath)
    }

    /**
     * Accepts only one short-lived token on this deployment's exact signed-object endpoint.
     * Redirects, fragments, userinfo, explicit ports and additional query parameters fail closed.
     */
    fun signedUrlOrNull(
        supabaseUrl: String,
        storageObject: ChatAttachmentStorageObject,
        candidateUrl: String,
    ): String? {
        val base = supabaseUrl.trim().trimEnd('/').takeIf(CanonicalSupabaseBase::matches) ?: return null
        val objectPrefix = "$base/storage/v1/object/sign/${storageObject.bucket}/${storageObject.path}?token="
        val candidate = candidateUrl.trim()
        if (!candidate.startsWith(objectPrefix)) return null
        val token = candidate.removePrefix(objectPrefix)
        if (!SignedToken.matches(token)) return null
        return candidate
    }

    private fun isCanonicalObjectPath(value: String): Boolean {
        if (value.length !in 3..1024 || value.startsWith('/') || value.endsWith('/')) return false
        if ('\\' in value || '?' in value || '#' in value || '%' in value) return false
        val segments = value.split('/')
        return segments.size >= 2 && segments.all { segment ->
            segment.isNotBlank() && segment != "." && segment != ".." && CanonicalPathSegment.matches(segment)
        }
    }

    private val CanonicalSupabaseBase = Regex("https://[a-z0-9-]+\\.supabase\\.co")
    private val CanonicalPathSegment = Regex("[A-Za-z0-9._-]+")
    private val SignedToken = Regex("[A-Za-z0-9._-]{16,4096}")
}
