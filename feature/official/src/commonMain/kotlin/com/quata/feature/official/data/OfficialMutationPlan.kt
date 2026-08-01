package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.core.text.toRemoteCommentBody
import com.quata.feature.official.domain.OfficialPostDraft

/** Pure protocol plans shared by platform transports; no credentials or network involved. */
data class OfficialMutationPlan(val method: String, val table: String, val filter: Map<String, String> = emptyMap(), val body: String? = null)

fun officialLikeLookupPlan(postId: String, profileId: String) = OfficialMutationPlan("GET", "official_post_likes", mapOf("select" to "id", "official_post_id" to "eq.$postId", "profile_id" to "eq.$profileId"))
fun officialLikeInsertPlan(postId: String, profileId: String) = OfficialMutationPlan("POST", "official_post_likes", body = "{\"official_post_id\":\"$postId\",\"profile_id\":\"$profileId\"}")
fun officialLikeDeletePlan(postId: String, profileId: String) = OfficialMutationPlan("DELETE", "official_post_likes", mapOf("official_post_id" to "eq.$postId", "profile_id" to "eq.$profileId"))
fun officialCommentPlan(postId: String, profileId: String, body: String) = OfficialMutationPlan("POST", "official_post_comments", body = "{\"official_post_id\":\"$postId\",\"profile_id\":\"$profileId\",\"body\":${officialJson(body)}}")
fun officialCommentPlan(postId: String, profileId: String, comment: PostComment) =
    officialCommentPlan(postId, profileId, comment.toRemoteCommentBody())
fun officialSoftDeletePlan(postId: String, groupId: String?, timestamp: String) = OfficialMutationPlan("PATCH", "official_posts", if (groupId.isNullOrBlank()) mapOf("id" to "eq.$postId") else mapOf("translation_group_id" to "eq.$groupId"), "{\"deleted_at\":${officialJson(timestamp)}}")
/** Client-safe PostgREST body; transport supplies the session bearer and checks authorisation first. */
fun officialPostInsertPlan(profileId: String, draft: OfficialPostDraft) = OfficialMutationPlan("POST", "official_posts", body = buildString {
    append("{\"profile_id\":${officialJson(profileId)},\"title\":${officialJson(draft.title)},\"summary\":${officialJson(draft.summary)},")
    append("\"content_html\":${officialJson(draft.contentHtml)},\"read_more_label\":${officialJson(draft.readMoreLabel)},")
    append("\"language\":${officialJson(draft.language.remoteValue)},\"post_type\":${officialJson(draft.type.remoteValue)},\"is_published\":true,\"is_live\":${draft.isLive}")
    draft.translationGroupId?.takeIf(String::isNotBlank)?.let { append(",\"translation_group_id\":${officialJson(it)}") }
    draft.mediaUrl?.takeIf(String::isNotBlank)?.let { append(",\"media_url\":${officialJson(it)}") }
    draft.mediaType?.let { append(",\"media_type\":${officialJson(it.remoteValue)}") }
    draft.linkUrl?.takeIf(String::isNotBlank)?.let { append(",\"link_url\":${officialJson(it)}") }
    append('}')
})
internal fun officialJson(value: String): String = buildString { append('"'); value.forEach { append(if (it == '"' || it == '\\') "\\$it" else it) }; append('"') }
fun officialCommentReportPayload(actorId: String, commentId: String): String =
    "{\"p_actor_profile_id\":${officialJson(actorId)},\"p_target_type\":\"official_comment\",\"p_target_id\":${officialJson(commentId)},\"p_reason\":\"other\"}"
