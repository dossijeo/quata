package com.quata.wordpress

data class AjaxEnvelope<T>(
    val success: Boolean,
    val data: T? = null,
    val errorMessage: String? = null,
    val rawJson: String = ""
)

data class RegistrationGuardData(
    val blocked: Boolean = false,
    val message: String? = null,
    val matchCount: Int = 0
)

data class RegistrationRecordData(
    val matchCount: Int = 0,
    val rawJson: String = ""
)

data class PasswordRecoveryData(
    val message: String? = null,
    val rawJson: String = ""
)

data class ModerationData(
    val action: String = "allow", // allow | review | block
    val reason: String? = null,
    val score: Int = 0,
    val message: String? = null,
    val rawJson: String = ""
)

data class ErrorReportData(
    val rawJson: String = ""
)

data class TrackVisitData(
    val rawJson: String = ""
)

data class VideoUploadData(
    val url: String? = null,
    val size: Long? = null,
    val mime: String? = null,
    val file: String? = null,
    val rawJson: String = ""
)

data class ProfileFollowStateData(
    val isFollowing: Boolean = false,
    val rawJson: String = ""
)

data class ProfileFollowStatsData(
    val followers: Int = 0,
    val following: Int = 0,
    val rawJson: String = ""
)

data class ProfileFollowConnectionsData(
    val rawJson: String = ""
)

data class ProfileFollowToggleData(
    val isFollowing: Boolean = false,
    val followers: Int = 0,
    val myFollowing: Int = 0,
    val rawJson: String = ""
)
