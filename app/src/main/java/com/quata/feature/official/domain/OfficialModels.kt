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
    val language: OfficialPostLanguage = OfficialPostLanguage.Spanish,
    val translationGroupId: String? = null,
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

enum class OfficialPostLanguage(val remoteValue: String) {
    Spanish("es"),
    English("en"),
    French("fr");

    companion object {
        fun fromRemote(value: String?): OfficialPostLanguage =
            entries.firstOrNull { it.remoteValue.equals(value, ignoreCase = true) } ?: Spanish

        fun fromAppLanguage(value: String?): OfficialPostLanguage =
            entries.firstOrNull { it.remoteValue.equals(value, ignoreCase = true) } ?: Spanish
    }
}

enum class OfficialReadMoreOption(val shortcode: String) {
    ReadMore("read_more"),
    MoreInformation("more_information"),
    ContinueReading("continue_reading"),
    Details("details");

    companion object {
        fun fromStored(value: String?): OfficialReadMoreOption? {
            val normalized = value?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.shortcode == normalized }
        }
    }
}

data class OfficialPostDraft(
    val title: String,
    val summary: String,
    val contentHtml: String,
    val readMoreLabel: String = "",
    val language: OfficialPostLanguage = OfficialPostLanguage.Spanish,
    val translationGroupId: String? = null,
    val type: OfficialPostType,
    val mediaUrl: String? = null,
    val mediaType: OfficialMediaType? = null,
    val linkUrl: String? = null,
    val isLive: Boolean = false
)
