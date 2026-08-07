package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.core.text.toRemoteCommentBody
import com.quata.feature.official.domain.OfficialPostDraft

/** Pure protocol plans shared by platform transports; no credentials or network involved. */
data class OfficialMutationPlan(val method: String, val table: String, val filter: Map<String, String> = emptyMap(), val body: String? = null)

fun officialPostCreatePlan(
    profileId: String,
    draft: OfficialPostDraft,
    translationGroupId: String,
    defaultTitle: String,
    publishedAt: String,
) = OfficialMutationPlan(
    method = "POST",
    table = "official_posts",
    body = buildString {
        append('{')
        append("\"profile_id\":").append(officialJson(profileId))
        append(",\"title\":").append(officialJson(draft.title.trim().ifBlank { defaultTitle }))
        append(",\"summary\":").append(officialNullableJson(draft.summary.trim()))
        append(",\"post_type\":").append(officialJson(draft.type.remoteValue))
        append(",\"content_html\":").append(officialJson(draft.officialContentHtmlOrFallback(defaultTitle)))
        append(",\"read_more_label\":").append(officialNullableJson(draft.readMoreLabel.trim()))
        append(",\"language\":").append(officialJson(draft.language.remoteValue))
        append(",\"translation_group_id\":").append(officialJson(draft.translationGroupId?.takeIf(String::isNotBlank) ?: translationGroupId))
        append(",\"media_url\":").append(officialNullableJson(draft.officialRemoteMediaUrlOrNull()))
        append(",\"media_type\":").append(officialNullableJson(draft.mediaType?.remoteValue?.takeIf { draft.mediaUrl?.isNotBlank() == true }))
        append(",\"link_url\":").append(officialNullableJson(draft.linkUrl?.trim().orEmpty()))
        append(",\"is_live\":").append(draft.isLive)
        append(",\"is_published\":true")
        append(",\"published_at\":").append(officialJson(publishedAt.trim()))
        append('}')
    },
)

fun officialPostCreatePlans(
    profileId: String,
    drafts: List<OfficialPostDraft>,
    translationGroupId: String,
    defaultTitle: String,
    publishedAt: String,
): List<OfficialMutationPlan> = drafts
    .filter { it.officialContentHtmlOrFallback(defaultTitle).officialPlainText().isNotBlank() }
    .map { officialPostCreatePlan(profileId, it, translationGroupId, defaultTitle, publishedAt) }

fun officialLikeLookupPlan(postId: String, profileId: String) = OfficialMutationPlan("GET", "official_post_likes", mapOf("select" to "id", "official_post_id" to "eq.$postId", "profile_id" to "eq.$profileId"))
fun officialLikeInsertPlan(postId: String, profileId: String) = OfficialMutationPlan("POST", "official_post_likes", body = "{\"official_post_id\":\"$postId\",\"profile_id\":\"$profileId\"}")
fun officialLikeDeletePlan(postId: String, profileId: String) = OfficialMutationPlan("DELETE", "official_post_likes", mapOf("official_post_id" to "eq.$postId", "profile_id" to "eq.$profileId"))
fun officialCommentPlan(postId: String, profileId: String, body: String) = OfficialMutationPlan("POST", "official_post_comments", body = "{\"official_post_id\":\"$postId\",\"profile_id\":\"$profileId\",\"body\":${officialJson(body)}}")
fun officialCommentPlan(postId: String, profileId: String, comment: PostComment) =
    officialCommentPlan(postId, profileId, comment.toRemoteCommentBody())
fun officialSoftDeletePlan(postId: String, groupId: String?, timestamp: String) = OfficialMutationPlan("PATCH", "official_posts", if (groupId.isNullOrBlank()) mapOf("id" to "eq.$postId") else mapOf("translation_group_id" to "eq.$groupId"), "{\"deleted_at\":${officialJson(timestamp)}}")
internal fun officialJson(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char < ' ') {
                append("\\u")
                append(char.code.toString(16).padStart(4, '0'))
            } else {
                append(char)
            }
        }
    }
    append('"')
}
fun officialCommentReportPayload(actorId: String, commentId: String): String =
    "{\"p_actor_profile_id\":${officialJson(actorId)},\"p_target_type\":\"official_comment\",\"p_target_id\":${officialJson(commentId)},\"p_reason\":\"other\"}"

private fun officialNullableJson(value: String?): String =
    value?.trim()?.takeIf(String::isNotBlank)?.let(::officialJson) ?: "null"

private fun OfficialPostDraft.officialContentHtmlOrFallback(defaultTitle: String): String {
    val richText = contentHtml.trim()
    if (richText.officialPlainText().isNotBlank()) return richText
    val fallback = summary.trim().ifBlank { title.trim() }.ifBlank { defaultTitle }
    return "<p>${fallback.officialEscapeHtml()}</p>"
}

private fun OfficialPostDraft.officialRemoteMediaUrlOrNull(): String? {
    val raw = mediaUrl?.trim()?.takeIf(String::isNotBlank) ?: return null
    require(raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        "official_media_upload_not_implemented"
    }
    return raw
}

private fun String.officialPlainText(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .trim()

private fun String.officialEscapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
