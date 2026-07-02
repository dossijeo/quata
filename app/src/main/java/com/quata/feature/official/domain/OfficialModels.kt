package com.quata.feature.official.domain

import com.quata.core.model.PostComment
import com.quata.core.model.User

data class OfficialPostItem(
    val id: String,
    val author: User,
    val title: String,
    val summary: String,
    val contentHtml: String,
    val contentPlain: String,
    val readMoreLabel: String = "",
    val type: OfficialPostType = OfficialPostType.Announcement,
    val mediaUrl: String? = null,
    val mediaType: OfficialMediaType? = null,
    val linkUrl: String? = null,
    val isLive: Boolean = false,
    val createdAt: String,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLikedByCurrentUser: Boolean = false,
    val comments: List<PostComment> = emptyList()
)

enum class OfficialPostType(val remoteValue: String) {
    Announcement("announcement"),
    News("news"),
    Event("event"),
    Urgent("urgent");

    companion object {
        fun fromRemote(value: String?): OfficialPostType =
            entries.firstOrNull { it.remoteValue == value } ?: Announcement
    }
}

enum class OfficialMediaType(val remoteValue: String) {
    Image("image"),
    Video("video");

    companion object {
        fun fromRemote(value: String?): OfficialMediaType? =
            entries.firstOrNull { it.remoteValue == value }
    }
}

data class OfficialPostDraft(
    val title: String,
    val summary: String,
    val contentHtml: String,
    val readMoreLabel: String = "",
    val type: OfficialPostType,
    val mediaUrl: String? = null,
    val mediaType: OfficialMediaType? = null,
    val linkUrl: String? = null,
    val isLive: Boolean = false
)
