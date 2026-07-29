package com.quata.feature.official.presentation

import com.quata.feature.official.data.IosOfficialRuntimeConfiguration
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Proves the Swift-facing public factory cannot accidentally require a session or enable writes. */
class IosOfficialPublicFactoryTest {
    @Test
    fun factoryBuildsAnonymousReadOnlyRepositoryForDeepLinkedPost() = runBlocking {
        val dependencies = iosPublicPostgrestReadOnlyOfficialHostDependencies(
            configuration = IosOfficialRuntimeConfiguration(
                supabaseUrl = "https://project.supabase.co",
                supabasePublishableKey = "public-client-key",
            ),
            officialPostId = "official-public-7",
            navigationMessage = "Explora Quata",
        )

        assertEquals("official-public-7", dependencies.officialPostId)
        assertEquals("Explora Quata", dependencies.navigationMessage)
        assertNull(dependencies.repository.refreshCurrentUser().getOrThrow())
        assertTrue(
            dependencies.repository.createPost(
                OfficialPostDraft(
                    title = "blocked",
                    summary = "blocked",
                    contentHtml = "<p>blocked</p>",
                    type = OfficialPostType.Announcement,
                ),
            ).isFailure,
        )
    }
}
