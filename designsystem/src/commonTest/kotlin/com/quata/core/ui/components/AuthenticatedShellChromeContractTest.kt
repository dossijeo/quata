package com.quata.core.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthenticatedShellChromeContractTest {
    @Test fun `content starts after safe top plus the fixed chrome`() {
        assertEquals(130.dp, AuthenticatedShellChromeContract.contentTop(62.dp, offline = false))
        assertEquals(158.dp, AuthenticatedShellChromeContract.contentTop(62.dp, offline = true))
    }

    @Test fun `bottom chrome consumes its height and the safe bottom exactly once`() {
        assertEquals(126.dp, AuthenticatedShellChromeContract.contentBottomInset(34.dp))
    }

    @Test fun `header positions retain Android authoritative dimensions`() {
        assertEquals(16.dp, AuthenticatedShellChromeContract.headerHorizontalInset)
        assertEquals(14.dp, AuthenticatedShellChromeContract.headerContentTopInset)
        assertEquals(54.dp, AuthenticatedShellChromeContract.notificationsOffset)
        assertEquals(70.dp, AuthenticatedShellChromeContract.sosWidth)
    }
}
