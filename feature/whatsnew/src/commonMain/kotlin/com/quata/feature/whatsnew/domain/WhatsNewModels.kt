package com.quata.feature.whatsnew.domain

data class PendingRelease(
    val releaseId: String,
    val versionCode: Long,
    val versionName: String?,
    val localizedNote: String,
    val availableLanguageTags: Set<String>
)

data class ReleaseNote(
    val languageTag: String,
    val text: String
)

data class UserReleaseState(
    val lastSeenVersionCode: Long?,
    val initializedAtVersionCode: Long?
)

sealed interface WhatsNewUiState {
    data object Loading : WhatsNewUiState
    data object Empty : WhatsNewUiState
    data class Content(val releases: List<PendingRelease>) : WhatsNewUiState {
        init {
            require(releases.isNotEmpty())
        }
    }
    data class Error(val recoverable: Boolean) : WhatsNewUiState
}

sealed interface StartupDestination {
    data object Loading : StartupDestination
    data object Main : StartupDestination
    data class WhatsNew(val releases: List<PendingRelease>) : StartupDestination {
        init {
            require(releases.isNotEmpty())
        }
    }
}
