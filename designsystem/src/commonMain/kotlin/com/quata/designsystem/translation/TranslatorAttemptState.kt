package com.quata.designsystem.translation

/**
 * State shared by the Chat and Comments translator overlays.
 *
 * [token] binds an asynchronous completion to the exact visible request that created it. A
 * completion is ignored after the box disappears, a retry starts, or another request replaces it.
 */
data class TranslatorAttemptState<T>(
    val result: T? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val token: Long = 0L,
) {
    companion object {
        fun <T> loading(token: Long): TranslatorAttemptState<T> {
            require(token > 0L) { "translator_attempt_token_must_be_positive" }
            return TranslatorAttemptState(loading = true, token = token)
        }
    }
}

/** Supplies overlay-lifetime request tokens without coupling the reducer to a platform clock. */
class TranslatorAttemptTokens {
    private var lastToken = 0L

    fun next(): Long {
        check(lastToken != Long.MAX_VALUE) { "translator_attempt_token_exhausted" }
        lastToken += 1L
        return lastToken
    }
}

fun <T> completeTranslatorAttempt(
    current: TranslatorAttemptState<T>?,
    token: Long,
    result: T?,
): TranslatorAttemptState<T>? {
    if (current?.loading != true || current.token != token) return current
    return result?.let { TranslatorAttemptState(result = it, token = token) }
        ?: TranslatorAttemptState(failed = true, token = token)
}

fun <T> failTranslatorAttempt(
    current: TranslatorAttemptState<T>?,
    token: Long,
): TranslatorAttemptState<T>? {
    if (current?.loading != true || current.token != token) return current
    return TranslatorAttemptState(failed = true, token = token)
}
