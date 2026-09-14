package com.quata.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Authorization belongs to one navigation and authentication decision, never a stale result. */
internal class WebPrivateRouteAccess(
    private val navigationRevision: () -> Long,
    private val navigationFragment: () -> String,
) {
    data class Ticket(val navigation: Long, val authentication: Long, val fragment: String)

    private var authenticationRevision by mutableLongStateOf(0L)
    private var accepted: Ticket? by mutableStateOf(null)
    val ticket: Ticket get() = Ticket(navigationRevision(), authenticationRevision, navigationFragment())
    val isAllowed: Boolean get() = accepted == ticket

    fun invalidateAuthentication() {
        authenticationRevision++
        accepted = null
    }

    suspend fun resolve(
        expected: Ticket,
        session: suspend () -> WebLocalSession?,
        onCurrentResult: (WebLocalSession?) -> Unit,
    ) {
        if (expected != ticket) return
        val result = session()
        currentCoroutineContext().ensureActive()
        if (expected != ticket) return
        onCurrentResult(result)
        if (expected == ticket && result != null) accepted = expected
    }
}
