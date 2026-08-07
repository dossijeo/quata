package com.quata.feature.official.presentation

import com.quata.core.ui.components.IosMemberProfileOpeningState
import com.quata.core.platform.FilePickerRequest
import com.quata.core.platform.FilePickerService
import com.quata.core.platform.PlatformFile
import com.quata.core.platform.PlatformResult
import com.quata.core.platform.VideoThumbnailService
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
            profileOpeningState = IosMemberProfileOpeningState(),
        )

        assertEquals("official-public-7", dependencies.officialPostId)
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

    @Test
    fun authenticatedEditorDependenciesMountRealRepositoryAndKeepPublishingFailClosed() = runBlocking {
        val dependencies = createIosOfficialEditorDependencies(
            repository = iosPublicPostgrestReadOnlyOfficialHostDependencies(
                configuration = IosOfficialRuntimeConfiguration(
                    supabaseUrl = "https://project.supabase.co",
                    supabasePublishableKey = "public-client-key",
                ),
                profileOpeningState = IosMemberProfileOpeningState(),
            ).repository,
            filePicker = EmptyFilePicker,
            videoThumbnails = EmptyVideoThumbnails,
            currentUserId = "official-user",
            preferredLanguageTag = "es-ES",
            onClose = {},
        )

        assertEquals("official-user", dependencies.currentUserId)
        assertTrue(
            dependencies.repository.createPosts(
                listOf(
                    OfficialPostDraft(
                        title = "blocked",
                        summary = "blocked",
                        contentHtml = "<p>blocked</p>",
                        type = OfficialPostType.Announcement,
                    ),
                ),
            ).isFailure,
        )
    }

    private object EmptyFilePicker : FilePickerService {
        override suspend fun pickFiles(
            acceptedMimeTypes: List<String>,
            allowMultiple: Boolean,
        ): PlatformResult<List<PlatformFile>> = PlatformResult.Unsupported

        override suspend fun pick(request: FilePickerRequest): PlatformResult<List<PlatformFile>> =
            PlatformResult.Unsupported
    }

    private object EmptyVideoThumbnails : VideoThumbnailService {
        override suspend fun createThumbnail(video: PlatformFile, maxWidth: Int): PlatformResult<PlatformFile> =
            PlatformResult.Unsupported
    }
}
