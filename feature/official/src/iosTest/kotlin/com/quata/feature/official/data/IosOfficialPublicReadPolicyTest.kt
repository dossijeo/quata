package com.quata.feature.official.data

import com.quata.core.model.PostComment
import com.quata.feature.official.domain.OfficialPostDraft
import com.quata.feature.official.domain.OfficialPostType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hermetic K/N contract tests for the anonymous Official PostgREST boundary. */
class IosOfficialPublicReadPolicyTest {
    @Test
    fun publicOfficialRequestUsesPublishableKeyAndNeverAuthorization() {
        val request = iosPublicOfficialRequest(
            baseUrl = " https://project.supabase.co/ ",
            publishableKey = " public-client-key ",
            table = "official_posts",
            query = mapOf(
                "select" to "id,title,content_html",
                "id" to "eq.post with/slash?and=unicode-ñ",
            ),
        )

        assertEquals("GET", request.method)
        assertEquals("public-client-key", request.headers["apikey"])
        assertEquals("application/json", request.headers["Accept"])
        assertFalse(request.headers.keys.any { it.equals("Authorization", ignoreCase = true) })
        assertEquals(
            "https://project.supabase.co/rest/v1/official_posts?" +
                "select=id%2Ctitle%2Ccontent_html&id=eq.post%20with%2Fslash%3Fand%3Dunicode-%C3%B1",
            request.url,
        )
    }

    @Test
    fun invalidTableFailsClosedBeforeAnyUrlSessionRequestCanExist() {
        assertFailsWith<IllegalArgumentException> {
            iosPublicOfficialRequest(
                baseUrl = "https://project.supabase.co",
                publishableKey = "public-client-key",
                table = "official_posts/../../profiles",
                query = emptyMap(),
            )
        }
    }

    @Test
    fun publicRepositoryDoesNotNeedSessionAndKeepsEveryWriteFailClosed() = runBlocking {
        val repository = IosOfficialReadRepository(
            IosOfficialRuntimeConfiguration("https://project.supabase.co", "public-client-key"),
        )

        // This must be a local no-session success: it proves anonymous composition does not try
        // to restore Keychain or contact Supabase merely to populate a current user.
        assertNull(repository.refreshCurrentUser().getOrThrow())
        val draft = OfficialPostDraft(
            title = "blocked",
            summary = "blocked",
            contentHtml = "<p>blocked</p>",
            type = OfficialPostType.Announcement,
        )
        val comment = PostComment("comment-1", "Anonymous", "blocked", "now")

        listOf(
            repository.createPost(draft),
            repository.createPosts(listOf(draft)),
            repository.deletePost("post-1"),
            repository.toggleLike("post-1"),
            repository.addComment("post-1", comment),
        ).forEach(::assertUnsupportedMutation)
    }

    @Test
    fun httpFailurePolicyIsFailClosedAndStable() {
        assertEquals(IosOfficialReadFailureKind.Unauthorized, iosOfficialReadFailureKind(401))
        assertEquals(IosOfficialReadFailureKind.RlsDenied, iosOfficialReadFailureKind(403))
        assertEquals(IosOfficialReadFailureKind.Network, iosOfficialReadFailureKind(null))
        listOf(0, 400, 404, 418, 500).forEach { status ->
            assertEquals(IosOfficialReadFailureKind.Http, iosOfficialReadFailureKind(status))
        }
    }

    private fun assertUnsupportedMutation(result: Result<*>) {
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is UnsupportedOperationException)
        assertEquals("ios_official_mutation_not_implemented", error?.message)
    }
}
