package com.quata.core.common

import android.content.Context
import com.quata.bettermessages.BetterMessagesHttpException
import com.quata.R
import com.quata.bettermessages.BetterMessagesBridgeException
import com.quata.data.supabase.SupabaseApiException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.json.JSONObject

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
        is BetterMessagesHttpException -> toBetterMessagesUserMessage(context)
        is BetterMessagesBridgeException -> context.getString(R.string.error_chat_session)
        is SocketTimeoutException -> context.getString(R.string.error_network_timeout)
        is UnknownHostException -> context.getString(R.string.error_network)
        is IOException -> context.getString(R.string.error_network)
        else -> message?.takeIf { it.isNotBlank() } ?: context.getString(fallbackMessageRes)
    }
}

private fun BetterMessagesHttpException.toBetterMessagesUserMessage(context: Context): String {
    val serverMessage = serverMessageOrNull()
    val isFileTypeRejection =
        serverMessage?.contains("tipo de archivo", ignoreCase = true) == true ||
            serverMessage?.contains("file type", ignoreCase = true) == true
    if (statusCode == 403 && isFileTypeRejection) {
        return context.getString(R.string.error_attachment_type_not_allowed)
    }
    if (statusCode == 403 && serverMessage?.contains("moderador", ignoreCase = true) == true) {
        return context.getString(R.string.error_conversation_leave_moderator)
    }
    if (!serverMessage.isNullOrBlank()) {
        return serverMessage
    }
    return when (statusCode) {
        400 -> context.getString(R.string.error_backend_bad_request)
        401, 403 -> context.getString(R.string.error_backend_unauthorized)
        in 500..599 -> context.getString(R.string.error_backend_unavailable)
        else -> context.getString(R.string.error_backend_generic)
    }
}

fun BetterMessagesHttpException.serverMessageOrNull(): String? =
    responseBody.extractJsonMessage()

private fun String.extractJsonMessage(): String? =
    runCatching {
        JSONObject(this).optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()
        ?: Regex(""""message"\s*:\s*"([^"]*)"""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\/", "/")
            ?.replace("\\\"", "\"")

fun <T> Result<T>.mapFailureToUserFacing(
    context: Context,
    fallbackMessageRes: Int = R.string.error_generic
): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(it.toUserFacingException(context, fallbackMessageRes)) }
    )
