package com.quata.designsystem.translation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TranslatorAttemptStateTest {
    @Test
    fun failure_can_retry_and_only_the_latest_attempt_can_publish_success() {
        val tokens = TranslatorAttemptTokens()
        val firstToken = tokens.next()
        val firstLoading = TranslatorAttemptState.loading<String>(firstToken)
        val firstFailure = failTranslatorAttempt(firstLoading, firstToken)

        assertTrue(firstFailure?.failed == true)
        assertFalse(firstFailure?.loading == true)

        val retryToken = tokens.next()
        val retryLoading = TranslatorAttemptState.loading<String>(retryToken)
        assertSame(
            retryLoading,
            completeTranslatorAttempt(retryLoading, firstToken, "stale"),
            "A completion from the failed request must not settle its retry.",
        )

        val success = completeTranslatorAttempt(retryLoading, retryToken, "Hola")
        assertEquals("Hola", success?.result)
        assertFalse(success?.loading == true)
        assertFalse(success?.failed == true)
    }

    @Test
    fun completion_after_the_visible_box_was_removed_stays_absent() {
        val token = TranslatorAttemptTokens().next()

        assertNull(completeTranslatorAttempt<String>(null, token, "late"))
        assertNull(failTranslatorAttempt<String>(null, token))
    }

    @Test
    fun null_translation_is_a_retryable_failure_for_the_current_attempt() {
        val token = TranslatorAttemptTokens().next()
        val loading = TranslatorAttemptState.loading<String>(token)

        val settled = completeTranslatorAttempt(loading, token, null)

        assertTrue(settled?.failed == true)
        assertFalse(settled?.loading == true)
        assertNull(settled?.result)
    }
}
