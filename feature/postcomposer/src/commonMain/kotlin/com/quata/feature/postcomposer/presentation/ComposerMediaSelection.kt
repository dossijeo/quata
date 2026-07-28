package com.quata.feature.postcomposer.presentation

import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult

/**
 * Returns a usable local picker result, if one was actually supplied.
 *
 * A cancelled, unsupported or failed picker request deliberately maps to null. Hosts must only
 * dispatch a media-selected event for a non-null value, preserving an already edited draft.
 */
fun PlatformResult<List<PlatformFile>>.composerSelectedFileOrNull(): PlatformFile? = when (this) {
    is PlatformResult.Success -> value.firstOrNull { it.reference.isNotBlank() }
    is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
}

/** Same no-mutation admission rule for the camera's single-file boundary. */
fun PlatformResult<PlatformFile>.composerCapturedFileOrNull(): PlatformFile? = when (this) {
    is PlatformResult.Success -> value.takeIf { it.reference.isNotBlank() }
    is PlatformResult.Failure, PlatformResult.Cancelled, PlatformResult.Unsupported -> null
}
