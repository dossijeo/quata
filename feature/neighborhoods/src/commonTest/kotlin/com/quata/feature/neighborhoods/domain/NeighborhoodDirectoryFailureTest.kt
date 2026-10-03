package com.quata.feature.neighborhoods.domain

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class NeighborhoodDirectoryFailureTest {
    @Test
    fun `authorization statuses use the portable directory failure`() {
        val cause = IllegalStateException("transport")

        assertIs<NeighborhoodDirectoryAccessDeniedException>(neighborhoodDirectoryFailure(401, cause))
        assertIs<NeighborhoodDirectoryAccessDeniedException>(neighborhoodDirectoryFailure(403, cause))
    }

    @Test
    fun `non authorization failures preserve the transport cause`() {
        val cause = IllegalStateException("offline")

        assertSame(cause, neighborhoodDirectoryFailure(null, cause))
        assertSame(cause, neighborhoodDirectoryFailure(500, cause))
    }
}
