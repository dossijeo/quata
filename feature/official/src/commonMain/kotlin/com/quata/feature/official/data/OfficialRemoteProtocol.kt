package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.core.model.User
import com.quata.core.text.decodeHtmlEntities
import com.quata.core.text.parsePostCommentBody
import com.quata.core.text.stripHtmlTagsAndDecode
import com.quata.feature.official.domain.OfficialMediaType
import com.quata.feature.official.domain.OfficialPostItem
import com.quata.feature.official.domain.OfficialPostType
import com.quata.feature.official.domain.OfficialPostLanguage

/** Portable PostgREST fields used to assemble the Official feed. */
data class OfficialRemotePost(
    val id: String,
    val profileId: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val postType: String? = null,
    val contentHtml: String? = null,
    val readMoreLabel: String? = null,
    val language: String? = null,
    val translationGroupId: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val linkUrl: String? = null,
    val isLive: Boolean = false,
    val publishedAt: String? = null,
    val createdAt: String? = null,
)

data class OfficialRemoteLike(val postId: String? = null, val profileId: String? = null)

data class OfficialRemoteComment(
    val id: String,
    val postId: String? = null,
    val profileId: String? = null,
    val body: String? = null,
    val createdAt: String? = null,
)

data class OfficialRemoteProfile(
    val id: String,
    val displayName: String? = null,
    val fallbackName: String? = null,
    val countryCode: String? = null,
    val phoneLocal: String? = null,
    val neighborhood: String? = null,
    val barrio: String? = null,
    val avatarUrl: String? = null,
    val avatar: String? = null,
    val isAdmin: Boolean = false,
    val isOfficial: Boolean = false,
)

/**
 * Scalar fields decoded by a platform PostgREST client before they enter the
 * portable Official protocol. JSON/Foundation parsing and response failures
 * deliberately remain platform concerns; the field-to-model mapping does not.
 */
class OfficialRemoteWireFields private constructor(
    private val values: Map<String, String?>,
) {
    operator fun get(name: String): String? = values[name]

    companion object {
        fun from(
            keys: Set<String>,
            valueForKey: (String) -> String?,
        ): OfficialRemoteWireFields = OfficialRemoteWireFields(keys.associateWith(valueForKey))
    }
}

/** The one PostgREST scalar vocabulary shared by the Web and iOS adapters. */
object OfficialRemoteWireSchema {
    val postScalarKeys: Set<String> = setOf(
        "profile_id", "title", "summary", "post_type", "content_html", "read_more_label",
        "language", "translation_group_id", "media_url", "media_type", "link_url", "is_live",
        "published_at", "created_at",
    )
    val commentScalarKeys: Set<String> = setOf("official_post_id", "profile_id", "body", "created_at")
    val likeScalarKeys: Set<String> = setOf("official_post_id", "profile_id")
    val profileScalarKeys: Set<String> = setOf(
        "display_name", "barrio", "neighborhood", "nombre", "avatar_url", "avatar", "is_admin", "is_official",
    )
}

fun officialRemotePostFromWire(
    id: String,
    fields: OfficialRemoteWireFields,
    isLive: Boolean,
): OfficialRemotePost = OfficialRemotePost(
    id = id,
    profileId = fields["profile_id"],
    title = fields["title"],
    summary = fields["summary"],
    postType = fields["post_type"],
    contentHtml = fields["content_html"],
    readMoreLabel = fields["read_more_label"],
    language = fields["language"],
    translationGroupId = fields["translation_group_id"],
    mediaUrl = fields["media_url"],
    mediaType = fields["media_type"],
    linkUrl = fields["link_url"],
    isLive = isLive,
    publishedAt = fields["published_at"],
    createdAt = fields["created_at"],
)

fun officialRemoteCommentFromWire(id: String, fields: OfficialRemoteWireFields): OfficialRemoteComment =
    OfficialRemoteComment(
        id = id,
        postId = fields["official_post_id"],
        profileId = fields["profile_id"],
        body = fields["body"],
        createdAt = fields["created_at"],
    )

fun officialRemoteLikeFromWire(fields: OfficialRemoteWireFields): OfficialRemoteLike = OfficialRemoteLike(
    postId = fields["official_post_id"],
    profileId = fields["profile_id"],
)

fun officialRemoteProfileFromWire(
    id: String,
    fields: OfficialRemoteWireFields,
    isAdmin: Boolean,
    isOfficial: Boolean,
): OfficialRemoteProfile = OfficialRemoteProfile(
    id = id,
    displayName = fields["display_name"],
    fallbackName = fields["nombre"],
    neighborhood = fields["neighborhood"],
    barrio = fields["barrio"],
    avatarUrl = fields["avatar_url"],
    avatar = fields["avatar"],
    isAdmin = isAdmin,
    isOfficial = isOfficial,
)

fun officialRemoteProfileIds(
    posts: List<OfficialRemotePost>,
    comments: List<OfficialRemoteComment> = emptyList(),
    likes: List<OfficialRemoteLike> = emptyList(),
): List<String> = (
    posts.mapNotNull(OfficialRemotePost::profileId) +
        comments.mapNotNull(OfficialRemoteComment::profileId) +
        likes.mapNotNull(OfficialRemoteLike::profileId)
).distinct()

/** Picks the requested locale for each group, with Spanish as the stable fallback. */
fun List<OfficialRemotePost>.selectOfficialTranslations(requestedLanguage: String?): List<OfficialRemotePost> {
    val requested = requestedLanguage?.substringBefore('-')?.lowercase()
    return groupBy { it.translationGroupId?.takeIf(String::isNotBlank) ?: it.id }.values
        .mapNotNull { variants -> variants.minWithOrNull(compareBy<OfficialRemotePost> {
            when (it.language?.lowercase()) { requested -> 0; "es" -> 1; else -> 2 }
        }.thenByDescending { it.publishedAt ?: it.createdAt.orEmpty() }) }
        .sortedByDescending { it.publishedAt ?: it.createdAt.orEmpty() }
}

/** Portable PostgREST plan: keep both locale variants until group selection is complete. */
data class OfficialTranslationReadPlan(val filters: Map<String, String>, val fetchLimit: Int)

fun officialTranslationReadPlan(requestedLanguage: String?, limit: Int, postId: String? = null): OfficialTranslationReadPlan {
    if (!postId.isNullOrBlank()) return OfficialTranslationReadPlan(emptyMap(), 1)
    val language = OfficialPostLanguage.fromAppLanguage(requestedLanguage?.substringBefore('-')).remoteValue
    return OfficialTranslationReadPlan(
        filters = if (language == "es") mapOf("language" to "eq.es") else mapOf("or" to "(language.eq.$language,language.eq.es)"),
        fetchLimit = limit.coerceAtLeast(1) * if (language == "es") 1 else 2,
    )
}

fun buildOfficialDomainPosts(
    posts: List<OfficialRemotePost>,
    comments: List<OfficialRemoteComment>,
    likes: List<OfficialRemoteLike>,
    profiles: List<OfficialRemoteProfile>,
    currentUserId: String?,
    defaultTitle: String,
    defaultCommentAuthor: String,
): List<OfficialPostItem> {
    val profilesById = profiles.associateBy(OfficialRemoteProfile::id)
    val likesByPostId = likes.groupBy(OfficialRemoteLike::postId)
    val commentsByPostId = comments.groupBy(OfficialRemoteComment::postId)
    return posts.map { post ->
        val author = profilesById[post.profileId]?.toOfficialDomainUser()
            ?: User(post.profileId.orEmpty().ifBlank { "official" }, "", defaultTitle, isOfficial = true)
        val postLikes = likesByPostId[post.id].orEmpty()
        val remoteComments = commentsByPostId[post.id].orEmpty()
        post.toOfficialDomain(
            author = author.copy(isOfficial = true),
            comments = remoteComments.toOfficialDomainComments(profilesById, defaultCommentAuthor),
            likesCount = postLikes.size,
            likedByCurrentUser = currentUserId != null && postLikes.any { it.profileId == currentUserId },
            defaultTitle = defaultTitle,
        )
    }
}

fun OfficialRemotePost.toOfficialDomain(
    author: User,
    comments: List<PostComment>,
    likesCount: Int,
    likedByCurrentUser: Boolean,
    defaultTitle: String,
): OfficialPostItem {
    val safeHtml = contentHtml.orEmpty()
    val safePlain = safeHtml.stripHtmlTagsAndDecode()
    val safeTitle = title?.decodeHtmlEntities()?.takeIf(String::isNotBlank)
        ?: safePlain.lineSequence().firstOrNull().orEmpty()
    return OfficialPostItem(
        id = id,
        author = author,
        title = safeTitle.ifBlank { defaultTitle },
        summary = summary?.decodeHtmlEntities()?.takeIf(String::isNotBlank) ?: safePlain.take(180),
        contentHtml = safeHtml,
        contentPlain = safePlain,
        readMoreLabel = readMoreLabel?.decodeHtmlEntities().orEmpty(),
        language = OfficialPostLanguage.fromRemote(language),
        translationGroupId = translationGroupId,
        type = OfficialPostType.fromRemote(postType),
        mediaUrl = mediaUrl,
        mediaType = OfficialMediaType.fromRemote(mediaType),
        linkUrl = linkUrl,
        isLive = isLive,
        createdAt = publishedAt ?: createdAt.orEmpty(),
        likesCount = likesCount,
        commentsCount = comments.size,
        isLikedByCurrentUser = likedByCurrentUser,
        comments = comments,
    )
}

fun List<OfficialRemoteComment>.toOfficialDomainComments(
    profilesById: Map<String, OfficialRemoteProfile>,
    defaultCommentAuthor: String,
): List<PostComment> {
    fun authorName(comment: OfficialRemoteComment): String =
        profilesById[comment.profileId]?.displayName?.takeIf(String::isNotBlank)
            ?: profilesById[comment.profileId]?.fallbackName?.takeIf(String::isNotBlank)
            ?: defaultCommentAuthor

    val parsedById = associate { it.id to it.body.orEmpty().decodeHtmlEntities().parsePostCommentBody() }
    return map { comment ->
        val parsed = parsedById.getValue(comment.id)
        val target = parsed.commentId?.let { targetId -> firstOrNull { it.id == targetId } }
        val targetParsed = target?.let { parsedById[it.id] }
        PostComment(
            id = comment.id,
            authorName = authorName(comment),
            message = parsed.message,
            timestamp = comment.createdAt.orEmpty(),
            authorId = comment.profileId,
            replyToAuthorName = parsed.authorName ?: target?.let(::authorName),
            replyToMessage = targetParsed?.message,
            replyToCommentId = parsed.commentId,
        )
    }
}

fun OfficialRemoteProfile.toOfficialDomainUser(): User = User(
    id = id,
    email = "${countryCode.orEmpty()}${phoneLocal.orEmpty()}@phone.quata.app",
    displayName = displayName?.takeIf(String::isNotBlank)
        ?: fallbackName?.takeIf(String::isNotBlank)
        ?: phoneLocal?.takeIf(String::isNotBlank)
        ?: "Usuario",
    neighborhood = neighborhood?.takeIf(String::isNotBlank) ?: barrio.orEmpty(),
    avatarUrl = avatarUrl ?: avatar,
    isAdmin = isAdmin,
    isOfficial = isOfficial,
)
