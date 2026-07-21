package com.quata.feature.whatsnew.presentation

import android.os.LocaleList
import com.quata.feature.whatsnew.domain.StartupDestination
import com.quata.feature.whatsnew.domain.WhatsNewRepository
import kotlinx.coroutines.withTimeoutOrNull

class StartupCoordinator(
    private val whatsNewRepository: WhatsNewRepository
) {
    suspend fun resolve(
        installedVersionCode: Long,
        locales: LocaleList,
        timeoutMillis: Long = StartupWhatsNewTimeoutMillis
    ): StartupDestination {
        val pending = withTimeoutOrNull(timeoutMillis) {
            whatsNewRepository.getPendingReleases(installedVersionCode, locales)
        }?.getOrNull()

        return pending
            ?.takeIf { it.isNotEmpty() }
            ?.let(StartupDestination::WhatsNew)
            ?: StartupDestination.Main
    }
}

const val StartupWhatsNewTimeoutMillis = 2_500L
