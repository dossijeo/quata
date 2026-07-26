package com.quata.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebPostComposerRepositoryTest {
    @Test
    fun requiresAnExplicitMembershipBeforeSelectingAComposerWall() {
        assertEquals("wall-1", requireComposerMembershipWallId(" wall-1 "))
        assertFailsWith<IllegalStateException> { requireComposerMembershipWallId(null) }
        assertFailsWith<IllegalStateException> { requireComposerMembershipWallId("  ") }
    }
}
