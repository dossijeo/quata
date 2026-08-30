package com.quata.web

internal fun webFeedOfficialCommentFailure(surface: String): String? {
    if (!webFeedOfficialCommentFailureEnabled()) return null
    val target = webFeedOfficialCommentFailureSurface()
    if (target != null && target.isNotBlank() && target != surface) return null
    return "feed_official_comments_e2e_forced_${surface}_comment_failure"
}

private fun webFeedOfficialCommentFailureEnabled(): Boolean =
    js("globalThis.localStorage?.getItem('quata.feedOfficialComments.forceFailure') === '1'")

private fun webFeedOfficialCommentFailureSurface(): String? =
    js("globalThis.localStorage?.getItem('quata.feedOfficialComments.forceFailure.surface')")
