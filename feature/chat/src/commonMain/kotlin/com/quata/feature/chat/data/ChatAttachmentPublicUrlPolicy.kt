package com.quata.feature.chat.data

/**
 * Strict allow-list for public Chat attachment URLs.
 *
 * Chat messages contain data received from other users, so a platform downloader must never use
 * an attachment reference as an arbitrary network URL. Uploads made by [PostgrestChatRepository]
 * use the canonical Supabase public-object shape represented here. Keeping this parser free of
 * platform URL APIs also makes the security boundary testable from common tests.
 */
object ChatAttachmentPublicUrlPolicy {
    const val LegacyBucket = "chat-attachments"
    const val PrivateBucket = "chat-attachments-private"
    @Deprecated("Use LegacyBucket or PrivateBucket explicitly")
    const val Bucket = LegacyBucket
    val AllowedBuckets = setOf(LegacyBucket, PrivateBucket)

    /**
     * Returns the canonical URL only when [publicUrl] is an HTTPS public-object URL for this
     * deployment's Chat bucket. Explicit ports, query strings, fragments, encoded characters and
     * path traversal are deliberately rejected instead of being normalised.
     */
    fun canonicalUrlOrNull(supabaseUrl: String, publicUrl: String): String? {
        val base = canonicalSupabaseBaseOrNull(supabaseUrl) ?: return null
        val candidate = publicUrl.trim()
        val bucket = AllowedBuckets.firstOrNull { candidate.startsWith("$base/storage/v1/object/public/$it/") }
            ?: return null
        val prefix = "$base/storage/v1/object/public/$bucket/"

        val objectPath = candidate.removePrefix(prefix)
        if (!isCanonicalObjectPath(objectPath)) return null
        return candidate
    }

    /**
     * Converts a persisted public-object reference into the same deployment's authenticated
     * Storage endpoint. The input remains deliberately strict so an attachment supplied by a
     * different user cannot turn a bearer-authenticated download into an arbitrary request.
     */
    fun authenticatedUrlOrNull(supabaseUrl: String, publicUrl: String): String? {
        val canonical = canonicalUrlOrNull(supabaseUrl, publicUrl) ?: return null
        val bucket = AllowedBuckets.first { "/storage/v1/object/public/$it/" in canonical }
        return canonical.replace(
            oldValue = "/storage/v1/object/public/$bucket/",
            newValue = "/storage/v1/object/authenticated/$bucket/",
        )
    }

    private fun canonicalSupabaseBaseOrNull(value: String): String? {
        val base = value.trim().trimEnd('/')
        return base.takeIf { CanonicalSupabaseBase.matches(it) }
    }

    private fun isCanonicalObjectPath(value: String): Boolean {
        if (value.isBlank() || value.length > MaxObjectPathLength) return false
        return value.split('/').all { segment ->
            segment.isNotEmpty() && segment.length <= MaxPathSegmentLength &&
                CanonicalPathSegment.matches(segment) && segment != "." && segment != ".."
        }
    }

    private const val MaxObjectPathLength = 512
    private const val MaxPathSegmentLength = 128
    private val CanonicalSupabaseBase = Regex("https://[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?")
    private val CanonicalPathSegment = Regex("[A-Za-z0-9._~-]+")
}
