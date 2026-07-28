package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PlatformFile

/** Prefix reserved by the iOS AVFoundation thumbnail adapter. */
const val IOS_COMPOSER_VIDEO_THUMBNAIL_PREFIX = "quata_video_thumbnail_"

/**
 * Narrow ownership check used before an iOS host removes a generated thumbnail.
 *
 * The caller provides already-resolved paths. This deliberately refuses files outside the exact
 * temporary directory or files merely containing the prefix in a parent/path segment.
 */
fun isOwnedIosComposerVideoThumbnailPath(path: String, temporaryDirectory: String): Boolean {
    val directory = temporaryDirectory.trim().replace('\\', '/').trimEnd('/')
    val candidate = path.trim().replace('\\', '/')
    if (directory.isEmpty() || !candidate.startsWith("$directory/")) return false
    val name = candidate.removePrefix("$directory/")
    return '/' !in name && name.startsWith(IOS_COMPOSER_VIDEO_THUMBNAIL_PREFIX) && name.endsWith(".png")
}

/** The thumbnail replaced by a new selection or draft clear; the caller owns physical deletion. */
fun iosComposerThumbnailToRelease(current: PlatformFile?, replacement: PlatformFile? = null): PlatformFile? =
    current?.takeIf { it.reference != replacement?.reference }
