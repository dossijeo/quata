package com.quata.core.common

import android.content.Context
import com.quata.R
import com.quata.bettermessages.BetterMessagesBridgeException
import com.quata.data.supabase.SupabaseApiException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class UserFacingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

fun Throwable.toUserFacingException(context: Context, fallbackMessageRes: Int = R.string.error_generic): UserFacingException {
    if (this is UserFacingException) return this
    return UserFacingException(toUserFacingMessage(context, fallbackMessageRes), this)
}

fun Throwable.toUserFacingMessage(context: Context, fallbackMessageRes: Int = R.string.error_generic): String {
    return when (this) {
        is UserFacingException -> message ?: context.getString(fallbackMessageRes)
        is SupabaseApiException -> when (statusCode) {
            400 -> context.getString(R.string.error_backend_bad_request)
            401, 403 -> context.getString(R.string.error_backend_unauthorized)
            in 500..599 -> context.getString(R.string.error_backend_unavailable)
            else -> context.getString(R.string.error_backend_generic)
        }
        is BetterMessagesBridgeException -> context.getString(R.string.error_chat_session)
        is SocketTimeoutException -> context.getString(R.string.error_network_timeout)
        is UnknownHostException -> context.getString(R.string.error_network)
        is IOException -> context.getString(R.string.error_network)
        else -> message?.takeIf { it.isNotBlank() } ?: context.getString(fallbackMessageRes)
    }
}

fun <T> Result<T>.mapFailureToUserFacing(
    context: Context,
    fallbackMessageRes: Int = R.string.error_generic
): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it.toUserFacingException(context, fallbackMessageRes)) }
    )
