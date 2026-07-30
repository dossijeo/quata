package com.quata.feature.official.data

/** Pure protocol plans shared by platform transports; no credentials or network involved. */
internal data class OfficialMutationPlan(val method: String, val table: String, val filter: Map<String, String> = emptyMap(), val body: String? = null)

internal fun officialLikeLookupPlan(postId: String, profileId: String) = OfficialMutationPlan("GET", "official_post_likes", mapOf("select" to "id", "official_post_id" to "eq.$postId", "profile_id" to "eq.$profileId"))
internal fun officialLikeInsertPlan(postId: String, profileId: String) = OfficialMutationPlan("POST", "official_post_likes", body = "{\"official_post_id\":\"$postId\",\"profile_id\":\"$profileId\"}")
internal fun officialLikeDeletePlan(postId: String, profileId: String) = OfficialMutationPlan("DELETE", "official_post_likes", mapOf("official_post_id" to "eq.$postId", "profile_id" to "eq.$profileId"))
internal fun officialCommentPlan(postId: String, profileId: String, body: String) = OfficialMutationPlan("POST", "official_post_comments", body = "{\"official_post_id\":\"$postId\",\"profile_id\":\"$profileId\",\"body\":${officialJson(body)}}")
internal fun officialSoftDeletePlan(postId: String, groupId: String?, timestamp: String) = OfficialMutationPlan("PATCH", "official_posts", if (groupId.isNullOrBlank()) mapOf("id" to "eq.$postId") else mapOf("translation_group_id" to "eq.$groupId"), "{\"deleted_at\":${officialJson(timestamp)}}")
internal fun officialJson(value: String): String = buildString { append('"'); value.forEach { append(if (it == '"' || it == '\\') "\\$it" else it) }; append('"') }
internal fun officialCommentReportPayload(actorId: String, commentId: String): String =
    "{\"p_actor_profile_id\":${officialJson(actorId)},\"p_target_type\":\"official_comment\",\"p_target_id\":${officialJson(commentId)},\"p_reason\":\"other\"}"
